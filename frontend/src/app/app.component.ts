import { AfterViewInit, Component, OnDestroy, isDevMode } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import * as L from 'leaflet';

import {
  OpenMeteoForecastResponse,
  RainViewerFrame,
  RainViewerManifest,
  RecommendationRequest,
  RecommendationDebugInfo,
  RecommendationService,
  RecommendedRoute,
  RouteType,
  Waypoint
} from './recommendation.service';

interface AirportLocation {
  code: string;
  name: string;
  latitude: number;
  longitude: number;
}

interface AircraftOption {
  id: string;
  name: string;
  cruiseSpeedKmh: number;
  fuelBurnLitersPerHour: number;
  fuelType: string;
  maxEnduranceHours: number;
  recommendedReserveMinutes: number;
}

interface WeatherTimelinePoint {
  latitude: number;
  longitude: number;
  routeRatio: number;
  temperatureCelsius: number;
  windKmh: number;
  windDirectionDegrees: number;
  cloudCoverPercent: number;
  precipitationProbability: number;
  visibilityKm: number;
}

type WeatherSamplePoint = Omit<
  WeatherTimelinePoint,
  'temperatureCelsius' | 'windKmh' | 'windDirectionDegrees' | 'cloudCoverPercent' | 'precipitationProbability' | 'visibilityKm'
>;

interface WeatherTimelineFrame {
  time: Date;
  elapsedMinutes: number;
  source: 'open-meteo' | 'mock';
  points: WeatherTimelinePoint[];
}

interface MapLayerState {
  route: boolean;
  weather: boolean;
  wind: boolean;
  waypoints: boolean;
  sun: boolean;
  aircraft: boolean;
}

const MOCK_FUEL_PRICES: Record<string, number> = {
  AVGAS_100LL: 2.85,
  JET_A1: 1.95,
  MOGAS: 1.75
};

const AIRPORT_OPTIONS: AirportLocation[] = [
  {
    code: 'GCLP',
    name: 'Gran Canaria',
    latitude: 27.9319,
    longitude: -15.3866
  },
  {
    code: 'GCTS',
    name: 'Tenerife Sur',
    latitude: 28.0445,
    longitude: -16.5725
  },
  {
    code: 'GCXO',
    name: 'Tenerife Norte',
    latitude: 28.4827,
    longitude: -16.3415
  }
];

const AIRPORTS: Record<string, AirportLocation> = Object.fromEntries(
  AIRPORT_OPTIONS.map((airport) => [airport.code, airport])
);

const AIRCRAFT_OPTIONS: AircraftOption[] = [
  {
    id: 'cessna-172',
    name: 'Cessna 172',
    cruiseSpeedKmh: 226,
    fuelBurnLitersPerHour: 34,
    fuelType: 'AVGAS_100LL',
    maxEnduranceHours: 4.4,
    recommendedReserveMinutes: 45
  },
  {
    id: 'piper-pa-28',
    name: 'Piper PA-28',
    cruiseSpeedKmh: 215,
    fuelBurnLitersPerHour: 36,
    fuelType: 'AVGAS_100LL',
    maxEnduranceHours: 5.0,
    recommendedReserveMinutes: 45
  },
  {
    id: 'diamond-da40',
    name: 'Diamond DA40',
    cruiseSpeedKmh: 235,
    fuelBurnLitersPerHour: 28,
    fuelType: 'JET_A1',
    maxEnduranceHours: 5.4,
    recommendedReserveMinutes: 45
  }
];

function currentLocalDateTimeValue(): string {
  const now = new Date();
  const offsetMilliseconds = now.getTimezoneOffset() * 60_000;

  return new Date(now.getTime() - offsetMilliseconds).toISOString().slice(0, 16);
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CurrencyPipe, DecimalPipe, FormsModule, NgFor, NgIf],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements AfterViewInit, OnDestroy {
  protected form: RecommendationRequest = {
    departureAirport: 'GCLP',
    availableFlightTimeMinutes: 75,
    aircraftId: 'cessna-172',
    cruiseSpeedKmh: 226,
    fuelBurnLitersPerHour: 34,
    fuelPricePerLiter: MOCK_FUEL_PRICES['AVGAS_100LL'],
    preference: 'coast',
    safetyMarginPercent: 15,
    plannedDepartureDateTime: currentLocalDateTimeValue(),
    weatherProvider: 'open-meteo'
  };

  protected readonly airports = AIRPORT_OPTIONS;
  protected readonly aircraftOptions = AIRCRAFT_OPTIONS;
  protected readonly preferences = [
    { value: 'any', label: 'Cualquiera' },
    { value: 'coast', label: 'Costa' },
    { value: 'mountain', label: 'Montana' },
    { value: 'short', label: 'Corta' },
    { value: 'scenic', label: 'Escenica' },
    { value: 'inter-island', label: 'Entre islas' }
  ];

  protected recommendations: RecommendedRoute[] = [];
  protected debugInfo?: RecommendationDebugInfo;
  protected readonly showDebugInfo = isDevMode();
  protected selectedRouteIndex = 0;
  protected isLoading = false;
  protected errorMessage = '';
  protected fuelType = AIRCRAFT_OPTIONS[0].fuelType;
  protected mapLayers: MapLayerState = {
    route: true,
    weather: false,
    wind: false,
    waypoints: false,
    sun: false,
    aircraft: true
  };
  protected weatherTimelineFrames: WeatherTimelineFrame[] = [];
  protected selectedWeatherFrameIndex = 0;
  protected weatherTimelineLoading = false;
  protected weatherTimelineError = '';
  protected timelinePlaying = false;
  protected mapExpanded = false;
  protected radarStatus = 'Radar real RainViewer disponible solo para frames recientes.';

  private fuelPriceEditedManually = false;
  private shouldFitMapOnNextRender = true;
  private weatherTimelineKey = '';
  private weatherTimelineSubscription?: Subscription;
  private rainViewerSubscription?: Subscription;
  private rainViewerManifest?: RainViewerManifest;
  private rainViewerLayer?: L.TileLayer;
  private timelineTimer?: number;

  private map?: L.Map;
  private readonly routesLayer = L.layerGroup();
  private readonly airportIcon = L.divIcon({
    className: 'airport-marker',
    html: '<span></span>',
    iconSize: [18, 18],
    iconAnchor: [9, 9]
  });
  private readonly waypointIcon = L.divIcon({
    className: 'waypoint-marker',
    html: '<span></span>',
    iconSize: [12, 12],
    iconAnchor: [6, 6]
  });
  private readonly orbitStartIcon = L.divIcon({
    className: 'orbit-start-marker',
    html: '<span>Inicio orbita</span>',
    iconSize: [92, 24],
    iconAnchor: [12, 12]
  });
  private readonly aircraftProgressIcon = L.divIcon({
    className: 'aircraft-progress-marker',
    html: '<span>AV</span>',
    iconSize: [30, 30],
    iconAnchor: [15, 15]
  });

  constructor(private readonly recommendationService: RecommendationService) {}

  ngAfterViewInit(): void {
    this.map = L.map('route-map', {
      zoomControl: true
    }).setView([27.95, -15.55], 8);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    this.routesLayer.addTo(this.map);
    this.renderMap();
  }

  ngOnDestroy(): void {
    this.stopTimelinePlayback();
    this.weatherTimelineSubscription?.unsubscribe();
    this.rainViewerSubscription?.unsubscribe();
    this.removeRainViewerLayer();
    this.map?.remove();
  }

  protected recommendRoutes(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.recommendations = [];
    this.debugInfo = undefined;
    this.selectedRouteIndex = 0;
    this.shouldFitMapOnNextRender = true;
    this.resetWeatherTimeline();
    this.renderMap();

    this.recommendationService.recommend(this.recommendationRequest()).subscribe({
      next: (response) => {
        this.recommendations = response.recommendations;
        this.debugInfo = response.debugInfo;
        this.selectedRouteIndex = 0;
        this.shouldFitMapOnNextRender = true;
        this.resetWeatherTimeline();
        this.isLoading = false;
        this.renderMap();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.resolveErrorMessage(error);
        this.debugInfo = undefined;
        this.resetWeatherTimeline();
        this.isLoading = false;
        this.renderMap();
      }
    });
  }

  protected selectRoute(index: number): void {
    this.selectedRouteIndex = index;
    this.shouldFitMapOnNextRender = true;
    this.resetWeatherTimeline();
    this.renderMap();
  }

  protected onMapLayerChanged(): void {
    this.renderMap();
  }

  protected toggleMapExpanded(): void {
    this.mapExpanded = !this.mapExpanded;
    this.shouldFitMapOnNextRender = true;
    setTimeout(() => {
      this.map?.invalidateSize();
      this.renderMap();
    }, 0);
  }

  protected selectedWeatherFrame(): WeatherTimelineFrame | undefined {
    return this.weatherTimelineFrames[this.selectedWeatherFrameIndex];
  }

  protected weatherTimelineLabel(): string {
    const frame = this.selectedWeatherFrame();
    if (!frame) {
      return 'Sin datos temporales';
    }

    const prefix = frame.elapsedMinutes < 0 ? '' : '+';
    return `${prefix}${Math.round(frame.elapsedMinutes)} min - ${frame.time.toLocaleString([], {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit'
    })}`;
  }

  protected weatherTimelineSourceLabel(): string {
    const frame = this.selectedWeatherFrame();
    if (!frame) {
      return 'La visualizacion se carga al seleccionar una ruta.';
    }

    if (frame.source === 'mock') {
      return 'Visualizacion simulada desde weather mock; no es radar ni satelite.';
    }

    return 'Forecast Open-Meteo horario interpolado espacial y temporalmente; no es imagen Meteosat/radar.';
  }

  protected onWeatherFrameChanged(value: string | number): void {
    this.selectedWeatherFrameIndex = Number(value);
    this.renderMap();
  }

  protected toggleTimelinePlayback(): void {
    if (this.timelinePlaying) {
      this.stopTimelinePlayback();
      return;
    }

    if (this.weatherTimelineFrames.length <= 1) {
      return;
    }

    if ((this.selectedWeatherFrame()?.elapsedMinutes ?? 0) < 0) {
      this.selectedWeatherFrameIndex = this.firstDepartureFrameIndex();
    }

    this.timelinePlaying = true;
    this.timelineTimer = window.setInterval(() => {
      this.selectedWeatherFrameIndex = (this.selectedWeatherFrameIndex + 1) % this.weatherTimelineFrames.length;
      if ((this.selectedWeatherFrame()?.elapsedMinutes ?? 0) < 0) {
        this.selectedWeatherFrameIndex = this.firstDepartureFrameIndex();
      }
      this.renderMap();
    }, 800);
  }

  protected selectedRoute(): RecommendedRoute | undefined {
    return this.recommendations[this.selectedRouteIndex];
  }

  protected selectedAircraft(): AircraftOption | undefined {
    return this.aircraftOptions.find((option) => option.id === this.form.aircraftId);
  }

  protected routeTypeLabel(routeType?: RouteType): string {
    switch (routeType) {
      case 'PREDEFINED':
        return 'Manual';
      case 'GENERATED_ONE_WAYPOINT':
        return 'Escenica simple';
      case 'GENERATED_TWO_WAYPOINTS':
        return 'Escenica corta';
      case 'GENERATED_THREE_OR_MORE_WAYPOINTS':
        return 'Escenica con referencias';
      default:
        return 'Manual';
    }
  }

  protected fuelPriceSourceLabel(source?: 'MANUAL' | 'MOCK'): string {
    return source === 'MANUAL' ? 'Manual' : 'Automatico mock';
  }

  protected weatherProviderLabel(route: RecommendedRoute): string {
    return route.weatherIsMock ? 'mock' : (route.weatherProvider ?? 'open-meteo');
  }

  protected aviationWeatherAgeText(ageMinutes?: number): string {
    if (ageMinutes === undefined || ageMinutes === null) {
      return 'edad no disponible';
    }

    if (ageMinutes < 60) {
      return `${ageMinutes} min`;
    }

    return `${Math.floor(ageMinutes / 60)} h ${ageMinutes % 60} min`;
  }

  protected aviationWindText(windDirectionDegrees?: number | string, windSpeedKt?: number, windGustKt?: number): string {
    if (windSpeedKt === undefined || windSpeedKt === null) {
      return 'sin viento decodificado';
    }

    const direction = windDirectionDegrees === undefined || windDirectionDegrees === null ? 'VRB' : windDirectionDegrees;
    const gust = windGustKt === undefined || windGustKt === null ? '' : ` G${windGustKt}`;

    return `${direction}/${windSpeedKt}${gust} kt`;
  }

  protected cloudLayersText(clouds?: { cover?: string; baseFeet?: number; type?: string }[]): string {
    if (!clouds?.length) {
      return 'Sin capas';
    }

    return clouds
      .map((cloud) => [cloud.cover, cloud.baseFeet ? `${cloud.baseFeet} ft` : undefined, cloud.type].filter(Boolean).join(' '))
      .join(', ');
  }

  protected sideLabel(side?: string): string {
    switch (side) {
      case 'LEFT':
        return 'izquierda';
      case 'RIGHT':
        return 'derecha';
      case 'BEHIND':
        return 'detras';
      case 'FRONT':
        return 'frente';
      case 'LOW_LIGHT':
        return 'luz baja';
      default:
        return 'sin determinar';
    }
  }

  protected frontalSunLegsText(route: RecommendedRoute): string {
    return route.frontalSunLegs?.length
      ? route.frontalSunLegs.join(', ')
      : 'Sin tramos frontales relevantes';
  }

  protected usefulFlightTimeMinutes(): number {
    const availableAfterReserveMinutes = Math.max(
      0,
      this.form.availableFlightTimeMinutes - (this.selectedAircraft()?.recommendedReserveMinutes ?? 0)
    );

    return availableAfterReserveMinutes * (100 - this.form.safetyMarginPercent) / 100;
  }

  protected routeUsefulTimeUsagePercent(route: RecommendedRoute): number {
    const usefulTimeMinutes = this.usefulFlightTimeMinutes();

    if (usefulTimeMinutes <= 0) {
      return 0;
    }

    return route.estimatedTimeMinutes / usefulTimeMinutes * 100;
  }

  protected routeTimeUsageText(route: RecommendedRoute): string {
    const usagePercent = Math.round(this.routeUsefulTimeUsagePercent(route));
    const sightseeingMinutes = route.sightseeingTimeMinutes ?? 0;
    const sightseeingText = sightseeingMinutes > 0
      ? ` Incluye ${Math.round(sightseeingMinutes)} min de observacion escenica local.`
      : '';

    if (usagePercent < 50) {
      return `Ruta corta: usa el ${usagePercent}% de tu tiempo util disponible.${sightseeingText}`;
    }

    return `Esta ruta usa el ${usagePercent}% de tu tiempo util disponible.${sightseeingText}`;
  }

  protected isTooShortByUsefulTime(route: RecommendedRoute): boolean {
    return this.routeUsefulTimeUsagePercent(route) < 50;
  }

  protected applyAircraftDefaults(): void {
    const aircraft = this.aircraftOptions.find((option) => option.id === this.form.aircraftId);

    if (!aircraft) {
      return;
    }

    this.form.cruiseSpeedKmh = aircraft.cruiseSpeedKmh;
    this.form.fuelBurnLitersPerHour = aircraft.fuelBurnLitersPerHour;
    this.fuelType = aircraft.fuelType;
    this.form.fuelPricePerLiter = MOCK_FUEL_PRICES[aircraft.fuelType] ?? null;
    this.fuelPriceEditedManually = false;
  }

  protected onFuelPriceChanged(value: number | null): void {
    this.fuelPriceEditedManually = value !== null;
  }

  private recommendationRequest(): RecommendationRequest {
    return {
      ...this.form,
      fuelPricePerLiter: this.fuelPriceEditedManually ? this.form.fuelPricePerLiter : null
    };
  }

  private renderMap(): void {
    if (!this.map) {
      return;
    }

    this.routesLayer.clearLayers();
    this.removeRainViewerLayer();

    const departureAirport = this.departureAirport();

    if (!departureAirport) {
      setTimeout(() => this.map?.invalidateSize(), 0);
      return;
    }

    this.addAirportMarker(departureAirport);

    if (this.mapLayers.route) {
      this.recommendations
        .filter((_, index) => index !== this.selectedRouteIndex)
        .forEach((route) => this.addRoutePolyline(route, this.routePoints(route, departureAirport), false));
    }

    const selectedRoute = this.selectedRoute();
    if (selectedRoute) {
      this.ensureWeatherTimeline(selectedRoute, departureAirport);
      if (this.mapLayers.route) {
        this.addSelectedRoutePath(selectedRoute, departureAirport);
        this.addSightseeingManeuvers(selectedRoute);
      }
      if (this.mapLayers.waypoints) {
        this.addWaypointMarkers(selectedRoute);
      }
      if (this.mapLayers.weather) {
        this.addWeatherOverlay(selectedRoute, departureAirport);
      }
      if (this.mapLayers.sun) {
        this.addSunDirection(selectedRoute, departureAirport);
      }
      if (this.mapLayers.route) {
        this.addFlightDirectionMarkers(selectedRoute, departureAirport);
      }
    if (this.mapLayers.aircraft) {
      this.addAnimatedAircraftMarker(selectedRoute, departureAirport);
    }
    }

    const selectedBounds = selectedRoute
      ? this.routeBounds(this.routePoints(selectedRoute, departureAirport))
      : L.latLngBounds([[departureAirport.latitude, departureAirport.longitude]]);

    if (selectedBounds.isValid() && this.shouldFitMapOnNextRender) {
      this.map.fitBounds(selectedBounds, {
        padding: [28, 28],
        maxZoom: 10
      });
      this.shouldFitMapOnNextRender = false;
    }

    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  private departureAirport(): AirportLocation | undefined {
    return AIRPORTS[this.form.departureAirport.trim().toUpperCase()];
  }

  private routePoints(route: RecommendedRoute, departureAirport: AirportLocation): L.LatLngExpression[] {
    if (route.flightPath?.length) {
      return route.flightPath.map((point) => [point.latitude, point.longitude] as L.LatLngExpression);
    }

    const departurePoint: L.LatLngExpression = [departureAirport.latitude, departureAirport.longitude];
    const points: L.LatLngExpression[] = [
      departurePoint,
      ...route.waypoints.map((waypoint: Waypoint) => [waypoint.latitude, waypoint.longitude] as L.LatLngExpression)
    ];

    if (route.waypoints.length > 0) {
      points.push(departurePoint);
    }

    return points;
  }

  private routeBounds(points: L.LatLngExpression[]): L.LatLngBounds {
    const bounds = L.latLngBounds([]);
    points.forEach((point) => bounds.extend(point));

    return bounds;
  }

  private addRoutePolyline(route: RecommendedRoute, points: L.LatLngExpression[], isSelected: boolean): void {
    if (points.length <= 1) {
      return;
    }

    L.polyline(points, {
      color: isSelected ? '#d9480f' : '#64748b',
      weight: isSelected ? 5 : 2,
      opacity: isSelected ? 0.95 : 0.22,
      lineCap: 'round',
      lineJoin: 'round'
    })
      .bindPopup(route.name)
      .addTo(this.routesLayer);
  }

  private addSelectedRoutePath(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const points = this.routePoints(route, departureAirport);
    this.addRoutePolyline(route, points, true);

    if (route.sightseeingManeuvers?.length) {
      this.selectedRouteNormalLegs(route, departureAirport).forEach((leg) => {
        L.polyline(leg, {
          color: '#7a2e0e',
          weight: 2,
          opacity: 0.35,
          dashArray: '8 10',
          lineCap: 'round',
          lineJoin: 'round'
        })
          .bindPopup(`${route.name} - tramo base`)
          .addTo(this.routesLayer);
      });
    }
  }

  private addWeatherOverlay(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const frame = this.selectedWeatherFrame();
    if (frame?.points.length) {
      this.addTemporalWeatherOverlay(route, frame);
      this.addRainViewerRadarLayer(frame);
      return;
    }

    const routePoints = this.routePoints(route, departureAirport);
    const midpoint = this.pointAtRouteRatio(routePoints, 0.5);
    if (!midpoint) {
      return;
    }

    const precipitation = route.routeWeatherSummary?.maxPrecipitationProbability ?? route.precipitationProbability ?? 0;
    const cloudCover = route.routeWeatherSummary?.averageCloudCoverPercent ?? route.cloudCoverPercent ?? 0;
    const wind = route.routeWeatherSummary?.averageWindKmh ?? route.windKmh ?? 0;
    const visibility = route.routeWeatherSummary?.minVisibilityKm ?? route.visibilityKm ?? 0;
    const weatherScore = route.routeWeatherSummary?.weatherScore ?? route.weatherScore ?? 0;
    const color = this.weatherOverlayColor(weatherScore, precipitation, cloudCover, wind, visibility);
    const popupText = this.weatherPopupText(route, wind, precipitation, cloudCover, visibility, weatherScore);

    L.circle(midpoint, {
      radius: this.weatherOverlayRadiusMeters(weatherScore, precipitation, cloudCover, wind, visibility),
      color,
      weight: 2,
      opacity: 0.8,
      fillColor: color,
      fillOpacity: 0.16
    })
      .bindPopup(popupText)
      .addTo(this.routesLayer);

    L.marker(midpoint, {
      icon: this.weatherVisualIcon(route, wind, precipitation, cloudCover, visibility, weatherScore),
      zIndexOffset: 600
    })
      .bindPopup(popupText)
      .addTo(this.routesLayer);

    [0.22, 0.78].forEach((ratio) => {
      const point = this.pointAtRouteRatio(routePoints, ratio);
      if (!point) {
        return;
      }

      L.circleMarker(point, {
        radius: 8,
        color,
        weight: 2,
        opacity: 0.9,
        fillColor: color,
        fillOpacity: 0.55
      })
        .bindPopup(popupText)
        .addTo(this.routesLayer);
    });
  }

  private weatherVisualIcon(
    route: RecommendedRoute,
    windKmh: number,
    precipitationProbability: number,
    cloudCoverPercent: number,
    visibilityKm: number,
    weatherScore: number
  ): L.DivIcon {
    return L.divIcon({
      className: [
        'weather-map-marker',
        this.weatherMarkerClass(weatherScore),
        this.cloudMarkerClass(cloudCoverPercent),
        this.rainMarkerClass(precipitationProbability),
        this.windMarkerClass(windKmh)
      ].join(' '),
      html: `
        <div
          class="weather-scene"
          aria-label="${this.weatherPopupText(route, windKmh, precipitationProbability, cloudCoverPercent, visibilityKm, weatherScore).replace(/<br>/g, '. ')}"
        >
          <span class="weather-glow"></span>
          <span class="cloud cloud-a"></span>
          <span class="cloud cloud-b"></span>
          <span class="cloud cloud-c"></span>
          <span class="wind wind-a"></span>
          <span class="wind wind-b"></span>
          <span class="rain rain-a"></span>
          <span class="rain rain-b"></span>
          <span class="rain rain-c"></span>
          <span class="rain rain-d"></span>
          <span class="rain rain-e"></span>
          <span class="rain rain-f"></span>
        </div>
      `,
      iconSize: [118, 78],
      iconAnchor: [59, 78]
    });
  }

  private weatherMarkerClass(weatherScore: number): string {
    if (weatherScore >= 75) {
      return 'good';
    }

    if (weatherScore >= 50) {
      return 'caution';
    }

    return 'poor';
  }

  private cloudMarkerClass(cloudCoverPercent: number): string {
    if (cloudCoverPercent >= 70) {
      return 'cloud-heavy';
    }

    if (cloudCoverPercent >= 35) {
      return 'cloud-medium';
    }

    return 'cloud-light';
  }

  private rainMarkerClass(precipitationProbability: number): string {
    if (precipitationProbability >= 55) {
      return 'rain-heavy';
    }

    if (precipitationProbability >= 25) {
      return 'rain-medium';
    }

    if (precipitationProbability >= 10) {
      return 'rain-light';
    }

    return 'rain-none';
  }

  private windMarkerClass(windKmh: number): string {
    if (windKmh >= 32) {
      return 'wind-strong';
    }

    if (windKmh >= 18) {
      return 'wind-medium';
    }

    return 'wind-light';
  }

  private weatherOverlayColor(
    weatherScore: number,
    precipitationProbability: number,
    cloudCoverPercent: number,
    windKmh: number,
    visibilityKm: number
  ): string {
    if (weatherScore < 50 || precipitationProbability >= 50 || windKmh >= 35 || visibilityKm < 10) {
      return '#b42318';
    }

    if (weatherScore < 75 || cloudCoverPercent >= 70 || precipitationProbability >= 25 || windKmh >= 25) {
      return '#d9480f';
    }

    return '#0f766e';
  }

  private weatherOverlayRadiusMeters(
    weatherScore: number,
    precipitationProbability: number,
    cloudCoverPercent: number,
    windKmh: number,
    visibilityKm: number
  ): number {
    const risk = Math.max(
      0,
      100 - weatherScore,
      precipitationProbability,
      cloudCoverPercent - 40,
      windKmh * 1.6,
      (20 - visibilityKm) * 4
    );

    return 4500 + Math.min(8500, risk * 90);
  }

  private weatherPopupText(
    route: RecommendedRoute,
    windKmh: number,
    precipitationProbability: number,
    cloudCoverPercent: number,
    visibilityKm: number,
    weatherScore: number
  ): string {
    return `Meteo ruta: ${Math.round(weatherScore)}/100<br>`
      + `Viento medio: ${Math.round(windKmh)} km/h<br>`
      + `Lluvia max.: ${Math.round(precipitationProbability)}%<br>`
      + `Nubes: ${Math.round(cloudCoverPercent)}%<br>`
      + `Visibilidad min.: ${Math.round(visibilityKm)} km<br>`
      + `Proveedor: ${this.weatherProviderLabel(route)}`;
  }

  private addSunDirection(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const sunAzimuth = route.sunAzimuthDegrees ?? 0;
    if (sunAzimuth <= 0) {
      return;
    }

    const start: L.LatLngExpression = [departureAirport.latitude, departureAirport.longitude];
    const lengthKm = 22;
    const radians = sunAzimuth * Math.PI / 180;
    const latitudeOffset = Math.cos(radians) * lengthKm / 111.32;
    const longitudeScale = 111.32 * Math.cos(departureAirport.latitude * Math.PI / 180);
    const longitudeOffset = longitudeScale === 0 ? 0 : Math.sin(radians) * lengthKm / longitudeScale;
    const end: L.LatLngExpression = [
      departureAirport.latitude + latitudeOffset,
      departureAirport.longitude + longitudeOffset
    ];

    L.polyline([start, end], {
      color: '#f59e0b',
      weight: 4,
      opacity: 0.8,
      dashArray: '3 8'
    })
      .bindPopup(`Direccion aproximada del sol: ${Math.round(sunAzimuth)} grados`)
      .addTo(this.routesLayer);

    L.marker(end, {
      icon: L.divIcon({
        className: 'sun-direction-marker',
        html: '<span>Sol</span>',
        iconSize: [46, 24],
        iconAnchor: [23, 12]
      })
    })
      .bindPopup(`Direccion aproximada del sol: ${Math.round(sunAzimuth)} grados`)
      .addTo(this.routesLayer);
  }

  private addFlightDirectionMarkers(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const legs = this.selectedRouteNormalLegs(route, departureAirport);
    legs.forEach((leg, index) => {
      const midpoint = this.midpoint(leg[0], leg[1]);
      const bearing = route.legOrientations?.[index]?.aircraftBearingDegrees ?? this.bearing(leg[0], leg[1]);
      const legInfo = route.legOrientations?.[index];
      const popup = legInfo
        ? `${legInfo.fromName} -> ${legInfo.toName}<br>Rumbo ${Math.round(legInfo.aircraftBearingDegrees)} grados<br>Sol ${this.sideLabel(legInfo.sunPosition)}`
        : `Direccion de vuelo ${Math.round(bearing)} grados`;

      L.marker(midpoint, {
        icon: L.divIcon({
          className: 'flight-direction-marker',
          html: `<span style="transform: rotate(${bearing}deg)">&#9650;</span>`,
          iconSize: [22, 22],
          iconAnchor: [11, 11]
        }),
        zIndexOffset: 500
      })
        .bindPopup(popup)
        .addTo(this.routesLayer);
    });
  }

  private addTemporalWeatherOverlay(route: RecommendedRoute, frame: WeatherTimelineFrame): void {
    frame.points.forEach((point) => {
      const weatherScore = this.estimatedWeatherScore(point);
      const color = this.weatherOverlayColor(
        weatherScore,
        point.precipitationProbability,
        point.cloudCoverPercent,
        point.windKmh,
        point.visibilityKm
      );
      const radius = 1800
        + Math.min(4200, point.cloudCoverPercent * 18 + point.precipitationProbability * 34 + point.windKmh * 36);
      const popupText = this.weatherTimelinePopupText(route, frame, point);

      L.circle([point.latitude, point.longitude], {
        radius,
        color,
        weight: 1,
        opacity: 0.42,
        fillColor: color,
        fillOpacity: 0.035 + Math.min(0.10, point.cloudCoverPercent / 900)
      })
        .bindPopup(popupText)
        .addTo(this.routesLayer);

      L.marker([point.latitude, point.longitude], {
        icon: this.weatherVisualIcon(
          route,
          point.windKmh,
          point.precipitationProbability,
          point.cloudCoverPercent,
          point.visibilityKm,
          weatherScore
        ),
        zIndexOffset: 610
      })
        .bindPopup(popupText)
        .addTo(this.routesLayer);

      if (this.mapLayers.wind) {
        this.addWindMarker(point, popupText);
      }
    });
  }

  private addWindMarker(point: WeatherTimelinePoint, popupText: string): void {
    L.marker([point.latitude, point.longitude], {
      icon: L.divIcon({
        className: 'wind-vector-marker',
        html: `<span style="transform: rotate(${(point.windDirectionDegrees + 180) % 360}deg)">&uarr;</span><strong>${Math.round(point.windKmh)}</strong>`,
        iconSize: [46, 42],
        iconAnchor: [23, 21]
      }),
      zIndexOffset: 620
    })
      .bindPopup(`${popupText}<br>Flecha: direccion aproximada hacia donde sopla el viento.`)
      .addTo(this.routesLayer);
  }

  private addRainViewerRadarLayer(frame: WeatherTimelineFrame): void {
    if (!this.map || frame.source !== 'open-meteo') {
      return;
    }

    if (!this.rainViewerManifest) {
      this.loadRainViewerManifest();
      return;
    }

    const radarFrame = this.closestRainViewerFrame(frame.time);
    if (!radarFrame) {
      this.radarStatus = 'Sin radar RainViewer para esta hora; se muestra visualizacion Open-Meteo interpolada.';
      return;
    }

    const url = `${this.rainViewerManifest.host}${radarFrame.path}/256/{z}/{x}/{y}/2/1_1.png`;
    this.rainViewerLayer = L.tileLayer(url, {
      opacity: 0.38,
      attribution: 'Weather radar data by RainViewer',
      maxNativeZoom: 7,
      zIndex: 350
    }).addTo(this.map);
    this.radarStatus = 'Capa de precipitacion radar real RainViewer para frame reciente; resto de variables son forecast Open-Meteo.';
  }

  private loadRainViewerManifest(): void {
    if (this.rainViewerSubscription) {
      return;
    }

    this.rainViewerSubscription = this.recommendationService.rainViewerManifest().subscribe({
      next: (manifest) => {
        this.rainViewerManifest = manifest;
        this.rainViewerSubscription = undefined;
        this.renderMap();
      },
      error: () => {
        this.rainViewerSubscription = undefined;
        this.radarStatus = 'No se pudo cargar RainViewer; se mantiene la visualizacion Open-Meteo interpolada.';
      }
    });
  }

  private closestRainViewerFrame(time: Date): RainViewerFrame | null {
    const frames = [
      ...(this.rainViewerManifest?.radar?.past ?? []),
      ...(this.rainViewerManifest?.radar?.nowcast ?? [])
    ];
    if (!frames.length) {
      return null;
    }

    let closestFrame: RainViewerFrame | null = null;
    let closestDistance = Number.MAX_SAFE_INTEGER;
    const targetSeconds = Math.round(time.getTime() / 1000);
    frames.forEach((frame) => {
      const distance = Math.abs(frame.time - targetSeconds);
      if (distance < closestDistance) {
        closestDistance = distance;
        closestFrame = frame;
      }
    });

    return closestDistance <= 30 * 60 ? closestFrame : null;
  }

  private removeRainViewerLayer(): void {
    if (this.map && this.rainViewerLayer) {
      this.map.removeLayer(this.rainViewerLayer);
    }
    this.rainViewerLayer = undefined;
  }

  private weatherTimelinePopupText(route: RecommendedRoute, frame: WeatherTimelineFrame, point: WeatherTimelinePoint): string {
    const source = frame.source === 'mock'
      ? 'mock simulado'
      : 'Open-Meteo forecast interpolado';
    return `Meteo visual ruta: ${Math.round(this.estimatedWeatherScore(point))}/100<br>`
      + `Hora: ${frame.time.toLocaleString()}<br>`
      + `Punto ruta: ${Math.round(point.routeRatio * 100)}%<br>`
      + `Viento: ${Math.round(point.windKmh)} km/h desde ${Math.round(point.windDirectionDegrees)} grados<br>`
      + `Precipitacion: ${Math.round(point.precipitationProbability)}%<br>`
      + `Nubosidad: ${Math.round(point.cloudCoverPercent)}%<br>`
      + `Visibilidad: ${Math.round(point.visibilityKm)} km<br>`
      + `Fuente visual: ${source}<br>`
      + `METAR/TAF: separado, solo en panel aeronautico.`;
  }

  private estimatedWeatherScore(point: WeatherTimelinePoint): number {
    const windScore = 100 - Math.min(100, point.windKmh / 45 * 100);
    const cloudScore = point.cloudCoverPercent <= 55
      ? 100 - Math.abs(point.cloudCoverPercent - 30) * 0.7
      : 82.5 - (point.cloudCoverPercent - 55) * 1.5;
    const precipitationScore = 100 - point.precipitationProbability * 1.35;
    const visibilityScore = point.visibilityKm >= 20 ? 100 : Math.max(0, point.visibilityKm / 20 * 100);

    return Math.max(0, Math.min(100, windScore * 0.3 + cloudScore * 0.2 + precipitationScore * 0.3 + visibilityScore * 0.2));
  }

  private addAnimatedAircraftMarker(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const points = this.routePoints(route, departureAirport);
    const frame = this.selectedWeatherFrame();
    const elapsedMinutes = frame ? Math.max(0, Math.min(route.estimatedTimeMinutes, frame.elapsedMinutes)) : 0;
    const ratio = route.estimatedTimeMinutes <= 0 ? 0 : elapsedMinutes / route.estimatedTimeMinutes;
    const point = this.pointAtRouteRatio(points, ratio);
    if (!point) {
      return;
    }

    const nextPoint = this.pointAtRouteRatio(points, Math.min(1, ratio + 0.01)) ?? point;
    const bearing = this.bearing(point, nextPoint);
    const trailPoints = this.routeProgressPoints(points, ratio);

    if (trailPoints.length > 1) {
      L.polyline(trailPoints, {
        color: '#ea580c',
        weight: 8,
        opacity: 0.82,
        lineCap: 'round',
        lineJoin: 'round'
      })
        .bindPopup(`Tramo recorrido estimado: +${Math.round(elapsedMinutes)} min`)
        .addTo(this.routesLayer);
    }

    L.marker(point, {
      icon: L.divIcon({
        className: 'animated-aircraft-marker',
        html: `
          <span class="aircraft-halo">
            <span class="aircraft-bearing" style="transform: rotate(${bearing}deg)">
              <span class="aircraft-nose"></span>
              <span class="aircraft-wing aircraft-wing-left"></span>
              <span class="aircraft-wing aircraft-wing-right"></span>
              <span class="aircraft-tail"></span>
            </span>
          </span>
        `,
        iconSize: [72, 72],
        iconAnchor: [36, 36]
      }),
      zIndexOffset: 2000
    })
      .bindPopup(`Avion estimado +${Math.round(elapsedMinutes)} min de ${Math.round(route.estimatedTimeMinutes)} min`)
      .addTo(this.routesLayer);
  }

  private routeProgressPoints(points: L.LatLngExpression[], ratio: number): L.LatLngExpression[] {
    if (points.length === 0) {
      return [];
    }

    const clampedRatio = Math.max(0, Math.min(1, ratio));
    const latLngs = points.map((point) => L.latLng(point));
    const segmentDistances = latLngs.slice(1).map((point, index) => latLngs[index].distanceTo(point));
    const totalDistance = segmentDistances.reduce((total, distance) => total + distance, 0);
    let targetDistance = totalDistance * clampedRatio;
    const progressPoints: L.LatLngExpression[] = [[latLngs[0].lat, latLngs[0].lng]];

    for (let index = 0; index < segmentDistances.length; index++) {
      const segmentDistance = segmentDistances[index];
      if (targetDistance >= segmentDistance) {
        progressPoints.push([latLngs[index + 1].lat, latLngs[index + 1].lng]);
        targetDistance -= segmentDistance;
        continue;
      }

      const start = latLngs[index];
      const end = latLngs[index + 1];
      const segmentRatio = segmentDistance === 0 ? 0 : targetDistance / segmentDistance;
      progressPoints.push([
        start.lat + (end.lat - start.lat) * segmentRatio,
        start.lng + (end.lng - start.lng) * segmentRatio
      ]);
      break;
    }

    return progressPoints;
  }

  private pointAtRouteRatio(points: L.LatLngExpression[], ratio: number): L.LatLngExpression | null {
    if (points.length === 0) {
      return null;
    }

    const latLngs = points.map((point) => L.latLng(point));
    const segmentDistances = latLngs.slice(1).map((point, index) => latLngs[index].distanceTo(point));
    const totalDistance = segmentDistances.reduce((total, distance) => total + distance, 0);
    let targetDistance = totalDistance * ratio;

    for (let index = 0; index < segmentDistances.length; index++) {
      const segmentDistance = segmentDistances[index];
      if (targetDistance <= segmentDistance) {
        const start = latLngs[index];
        const end = latLngs[index + 1];
        const segmentRatio = segmentDistance === 0 ? 0 : targetDistance / segmentDistance;

        return [
          start.lat + (end.lat - start.lat) * segmentRatio,
          start.lng + (end.lng - start.lng) * segmentRatio
        ];
      }

      targetDistance -= segmentDistance;
    }

    const lastPoint = latLngs[latLngs.length - 1];
    return [lastPoint.lat, lastPoint.lng];
  }

  private midpoint(first: L.LatLngExpression, second: L.LatLngExpression): L.LatLngExpression {
    const start = L.latLng(first);
    const end = L.latLng(second);

    return [(start.lat + end.lat) / 2, (start.lng + end.lng) / 2];
  }

  private bearing(first: L.LatLngExpression, second: L.LatLngExpression): number {
    const start = L.latLng(first);
    const end = L.latLng(second);
    const fromLatitude = start.lat * Math.PI / 180;
    const toLatitude = end.lat * Math.PI / 180;
    const longitudeDelta = (end.lng - start.lng) * Math.PI / 180;
    const y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
    const x = Math.cos(fromLatitude) * Math.sin(toLatitude)
      - Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);

    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
  }

  private selectedRouteNormalLegs(route: RecommendedRoute, departureAirport: AirportLocation): L.LatLngExpression[][] {
    const departurePoint: L.LatLngExpression = [departureAirport.latitude, departureAirport.longitude];
    const normalPoints: L.LatLngExpression[] = [
      departurePoint,
      ...route.waypoints.map((waypoint) => [waypoint.latitude, waypoint.longitude] as L.LatLngExpression),
      departurePoint
    ];
    const legs: L.LatLngExpression[][] = [];

    for (let index = 0; index < normalPoints.length - 1; index++) {
      legs.push([normalPoints[index], normalPoints[index + 1]]);
    }

    return legs;
  }

  private addSightseeingManeuvers(route: RecommendedRoute): void {
    route.sightseeingManeuvers?.forEach((maneuver) => {
      const waypoint = route.waypoints.find((candidate) => candidate.name === maneuver.waypointName);

      if (!waypoint) {
        return;
      }

      L.circle([waypoint.latitude, waypoint.longitude], {
        radius: maneuver.radiusKm * 1000,
        color: '#7c3aed',
        weight: 2,
        opacity: 0.65,
        fillColor: '#8b5cf6',
        fillOpacity: 0.08,
        dashArray: '6 8'
      })
        .bindPopup(maneuver.instruction)
        .addTo(this.routesLayer);

      const orbitPoints = maneuver.orbitPath?.map((point) => [point.latitude, point.longitude] as L.LatLngExpression) ?? [];
      if (orbitPoints.length > 1) {
        L.polyline(orbitPoints, {
          color: '#7c3aed',
          weight: 5,
          opacity: 0.95,
          lineCap: 'round',
          lineJoin: 'round'
        })
          .bindPopup(maneuver.instruction)
          .addTo(this.routesLayer);

        const firstOrbitPoint = maneuver.orbitPath?.[0];
        if (firstOrbitPoint) {
          L.marker([firstOrbitPoint.latitude, firstOrbitPoint.longitude], {
            icon: this.orbitStartIcon
          })
            .bindPopup(`Inicio orbita<br>${maneuver.instruction}`)
            .addTo(this.routesLayer);
        }
      }
    });
  }

  private addWaypointMarkers(route: RecommendedRoute): void {
    route.waypoints.forEach((waypoint) => {
      L.marker([waypoint.latitude, waypoint.longitude], {
        icon: this.waypointIcon
      })
        .bindPopup(`${waypoint.name}<br>${route.name}`)
        .addTo(this.routesLayer);
    });
  }

  private addAirportMarker(airport: AirportLocation): void {
    L.marker([airport.latitude, airport.longitude], {
      icon: this.airportIcon
    })
      .bindPopup(`${airport.code} - ${airport.name}`)
      .addTo(this.routesLayer);
  }

  private ensureWeatherTimeline(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const key = this.weatherTimelineCacheKey(route, departureAirport);
    if (this.weatherTimelineKey === key || this.weatherTimelineLoading) {
      return;
    }

    this.weatherTimelineKey = key;
    this.weatherTimelineFrames = [];
    this.selectedWeatherFrameIndex = 0;
    this.weatherTimelineError = '';

    if (route.weatherIsMock || route.routeWeatherSummary?.isMock || this.weatherProviderLabel(route) === 'mock') {
      this.weatherTimelineFrames = this.mockWeatherTimeline(route, departureAirport);
      return;
    }

    const samplePoints = this.weatherSamplePoints(route, departureAirport);
    const departureTime = this.departureTime(route);
    const endTime = new Date(departureTime.getTime() + (route.estimatedTimeMinutes + 90) * 60_000);
    const startTime = new Date(departureTime.getTime() - 90 * 60_000);
    this.weatherTimelineLoading = true;
    this.weatherTimelineSubscription?.unsubscribe();
    this.weatherTimelineSubscription = this.recommendationService.weatherForecast(
      samplePoints.map((point) => point.latitude),
      samplePoints.map((point) => point.longitude),
      this.isoDateOnly(startTime),
      this.isoDateOnly(endTime)
    ).subscribe({
      next: (response) => {
        const forecasts = Array.isArray(response) ? response : [response];
        this.weatherTimelineFrames = this.openMeteoWeatherTimeline(route, departureAirport, forecasts);
        this.weatherTimelineLoading = false;
        this.renderMap();
      },
      error: () => {
        this.weatherTimelineError = 'No se pudo cargar la evolucion Open-Meteo; se mantiene el resumen meteorologico de ruta.';
        this.weatherTimelineLoading = false;
        this.renderMap();
      }
    });
  }

  private resetWeatherTimeline(): void {
    this.stopTimelinePlayback();
    this.weatherTimelineSubscription?.unsubscribe();
    this.weatherTimelineSubscription = undefined;
    this.weatherTimelineKey = '';
    this.weatherTimelineFrames = [];
    this.selectedWeatherFrameIndex = 0;
    this.weatherTimelineLoading = false;
    this.weatherTimelineError = '';
    this.radarStatus = 'Radar real RainViewer disponible solo para frames recientes.';
    this.removeRainViewerLayer();
  }

  private stopTimelinePlayback(): void {
    if (this.timelineTimer !== undefined) {
      window.clearInterval(this.timelineTimer);
    }
    this.timelineTimer = undefined;
    this.timelinePlaying = false;
  }

  private weatherTimelineCacheKey(route: RecommendedRoute, departureAirport: AirportLocation): string {
    return [
      route.id,
      route.plannedDepartureDateTime ?? this.form.plannedDepartureDateTime,
      route.estimatedTimeMinutes,
      this.weatherProviderLabel(route),
      departureAirport.code
    ].join(':');
  }

  private openMeteoWeatherTimeline(
    route: RecommendedRoute,
    departureAirport: AirportLocation,
    forecasts: OpenMeteoForecastResponse[]
  ): WeatherTimelineFrame[] {
    const samplePoints = this.weatherSamplePoints(route, departureAirport);
    const departureTime = this.departureTime(route);

    return this.timelineElapsedMinutes(route).map((elapsedMinutes) => {
      const frameTime = new Date(departureTime.getTime() + elapsedMinutes * 60_000);
      return {
        time: frameTime,
        elapsedMinutes,
        source: 'open-meteo',
        points: samplePoints.map((samplePoint, index) => this.openMeteoPointAtTime(
          samplePoint,
          forecasts[Math.min(index, forecasts.length - 1)],
          frameTime
        ))
      };
    });
  }

  private mockWeatherTimeline(route: RecommendedRoute, departureAirport: AirportLocation): WeatherTimelineFrame[] {
    const samplePoints = this.weatherSamplePoints(route, departureAirport);
    const departureTime = this.departureTime(route);
    const baseWind = route.routeWeatherSummary?.averageWindKmh ?? route.windKmh ?? 12;
    const baseCloud = route.routeWeatherSummary?.averageCloudCoverPercent ?? route.cloudCoverPercent ?? 30;
    const basePrecipitation = route.routeWeatherSummary?.maxPrecipitationProbability ?? route.precipitationProbability ?? 5;
    const baseVisibility = route.routeWeatherSummary?.minVisibilityKm ?? route.visibilityKm ?? 30;
    const baseTemperature = route.routeWeatherSummary?.averageTemperatureCelsius ?? route.temperatureCelsius ?? 22;

    return this.timelineElapsedMinutes(route).map((elapsedMinutes) => {
      const frameTime = new Date(departureTime.getTime() + elapsedMinutes * 60_000);
      return {
        time: frameTime,
        elapsedMinutes,
        source: 'mock',
        points: samplePoints.map((samplePoint, index) => {
          const phase = elapsedMinutes / Math.max(route.estimatedTimeMinutes, 1) + index * 0.35;
          return {
            ...samplePoint,
            temperatureCelsius: this.round(baseTemperature + Math.sin(phase) * 1.2),
            windKmh: this.round(Math.max(0, baseWind + Math.sin(phase * 1.7) * 4)),
            windDirectionDegrees: (70 + index * 38 + elapsedMinutes * 0.8) % 360,
            cloudCoverPercent: this.clampPercent(baseCloud + Math.sin(phase * 1.2) * 16),
            precipitationProbability: this.clampPercent(basePrecipitation + Math.max(0, Math.sin(phase * 1.5)) * 18),
            visibilityKm: this.round(Math.max(1, baseVisibility - Math.max(0, Math.sin(phase * 1.5)) * 5))
          };
        })
      };
    });
  }

  private openMeteoPointAtTime(samplePoint: WeatherSamplePoint, forecast: OpenMeteoForecastResponse, time: Date): WeatherTimelinePoint {
    const hourly = forecast.hourly;
    const interpolation = this.hourlyInterpolation(hourly.time, time);

    return {
      ...samplePoint,
      temperatureCelsius: this.interpolateHourly(hourly.temperature_2m, interpolation, 22),
      windKmh: this.interpolateHourly(hourly.wind_speed_10m, interpolation, 12),
      windDirectionDegrees: this.interpolateHourly(hourly.wind_direction_10m, interpolation, 0),
      cloudCoverPercent: this.clampPercent(this.interpolateHourly(hourly.cloud_cover, interpolation, 30)),
      precipitationProbability: this.clampPercent(this.interpolateHourly(hourly.precipitation_probability, interpolation, 0)),
      visibilityKm: this.round(this.interpolateHourly(hourly.visibility, interpolation, 10000) / 1000)
    };
  }

  private hourlyInterpolation(times: string[], time: Date): { startIndex: number; endIndex: number; ratio: number } {
    if (!times?.length) {
      return { startIndex: 0, endIndex: 0, ratio: 0 };
    }

    const target = time.getTime();
    const parsedTimes = times.map((value) => new Date(value).getTime());
    for (let index = 0; index < parsedTimes.length - 1; index++) {
      if (target >= parsedTimes[index] && target <= parsedTimes[index + 1]) {
        const span = parsedTimes[index + 1] - parsedTimes[index];
        return {
          startIndex: index,
          endIndex: index + 1,
          ratio: span <= 0 ? 0 : (target - parsedTimes[index]) / span
        };
      }
    }

    const closestIndex = parsedTimes.reduce((closest, value, index) => (
      Math.abs(value - target) < Math.abs(parsedTimes[closest] - target) ? index : closest
    ), 0);
    return { startIndex: closestIndex, endIndex: closestIndex, ratio: 0 };
  }

  private interpolateHourly(
    values: number[] | undefined,
    interpolation: { startIndex: number; endIndex: number; ratio: number },
    fallback: number
  ): number {
    if (!values?.length) {
      return fallback;
    }

    const start = values[interpolation.startIndex] ?? fallback;
    const end = values[interpolation.endIndex] ?? start;
    return this.round(start + (end - start) * interpolation.ratio);
  }

  private weatherSamplePoints(route: RecommendedRoute, departureAirport: AirportLocation): WeatherSamplePoint[] {
    const points = this.routePoints(route, departureAirport);
    return [0.15, 0.5, 0.85]
      .map((routeRatio) => {
        const point = this.pointAtRouteRatio(points, routeRatio);
        return point ? { latitude: L.latLng(point).lat, longitude: L.latLng(point).lng, routeRatio } : null;
      })
      .filter((point): point is WeatherSamplePoint => point !== null);
  }

  private timelineElapsedMinutes(route: RecommendedRoute): number[] {
    const stepMinutes = route.estimatedTimeMinutes > 120 ? 2 : 1;
    const endMinute = Math.ceil((route.estimatedTimeMinutes + 60) / stepMinutes) * stepMinutes;
    const minutes: number[] = [];
    for (let minute = -60; minute <= endMinute; minute += stepMinutes) {
      minutes.push(minute);
    }

    return minutes;
  }

  private departureTime(route: RecommendedRoute): Date {
    return new Date(route.plannedDepartureDateTime ?? this.form.plannedDepartureDateTime);
  }

  private isoDateOnly(date: Date): string {
    const offsetMilliseconds = date.getTimezoneOffset() * 60_000;
    return new Date(date.getTime() - offsetMilliseconds).toISOString().slice(0, 10);
  }

  private clampPercent(value: number): number {
    return this.round(Math.max(0, Math.min(100, value)));
  }

  private round(value: number): number {
    return Math.round(value * 100) / 100;
  }

  private firstDepartureFrameIndex(): number {
    const index = this.weatherTimelineFrames.findIndex((frame) => frame.elapsedMinutes >= 0);
    return index >= 0 ? index : 0;
  }

  private resolveErrorMessage(error: HttpErrorResponse): string {
    if (error.error?.errors?.length) {
      return error.error.errors.join(' ');
    }

    if (error.error?.message) {
      return error.error.message;
    }

    return 'No se han podido obtener recomendaciones.';
  }
}
