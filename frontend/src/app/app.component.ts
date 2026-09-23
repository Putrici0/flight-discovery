import { AfterViewInit, Component, OnDestroy, isDevMode } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import * as L from 'leaflet';

import {
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

  private fuelPriceEditedManually = false;

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
    this.map?.remove();
  }

  protected recommendRoutes(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.recommendations = [];
    this.debugInfo = undefined;
    this.selectedRouteIndex = 0;
    this.renderMap();

    this.recommendationService.recommend(this.recommendationRequest()).subscribe({
      next: (response) => {
        this.recommendations = response.recommendations;
        this.debugInfo = response.debugInfo;
        this.selectedRouteIndex = 0;
        this.isLoading = false;
        this.renderMap();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.resolveErrorMessage(error);
        this.debugInfo = undefined;
        this.isLoading = false;
        this.renderMap();
      }
    });
  }

  protected selectRoute(index: number): void {
    this.selectedRouteIndex = index;
    this.renderMap();
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

    const departureAirport = this.departureAirport();

    if (!departureAirport) {
      setTimeout(() => this.map?.invalidateSize(), 0);
      return;
    }

    this.addAirportMarker(departureAirport);

    this.recommendations
      .filter((_, index) => index !== this.selectedRouteIndex)
      .forEach((route) => this.addRoutePolyline(route, this.routePoints(route, departureAirport), false));

    const selectedRoute = this.selectedRoute();
    if (selectedRoute) {
      this.addSelectedRoutePath(selectedRoute, departureAirport);
      this.addSightseeingManeuvers(selectedRoute);
      this.addWaypointMarkers(selectedRoute);
      this.addWeatherOverlay(selectedRoute, departureAirport);
      this.addSunDirection(selectedRoute, departureAirport);
      this.addFlightDirectionMarkers(selectedRoute, departureAirport);
      this.addProgressMarkers(selectedRoute, departureAirport);
    }

    const selectedBounds = selectedRoute
      ? this.routeBounds(this.routePoints(selectedRoute, departureAirport))
      : L.latLngBounds([[departureAirport.latitude, departureAirport.longitude]]);

    if (selectedBounds.isValid()) {
      this.map.fitBounds(selectedBounds, {
        padding: [28, 28],
        maxZoom: 10
      });
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
      iconSize: [188, 126],
      iconAnchor: [94, 126]
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
          html: `<span style="transform: rotate(${bearing}deg)">▲</span>`,
          iconSize: [22, 22],
          iconAnchor: [11, 11]
        }),
        zIndexOffset: 500
      })
        .bindPopup(popup)
        .addTo(this.routesLayer);
    });
  }

  private addProgressMarkers(route: RecommendedRoute, departureAirport: AirportLocation): void {
    const points = this.routePoints(route, departureAirport);
    [0.25, 0.5, 0.75].forEach((ratio) => {
      const point = this.pointAtRouteRatio(points, ratio);
      if (!point) {
        return;
      }

      const elapsedMinutes = Math.round(route.estimatedTimeMinutes * ratio);
      L.marker(point, {
        icon: this.aircraftProgressIcon
      })
        .bindPopup(`Posicion estimada +${elapsedMinutes} min`)
        .addTo(this.routesLayer);
    });
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
