import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import * as L from 'leaflet';

import {
  RecommendationRequest,
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
}

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
    fuelBurnLitersPerHour: 34
  },
  {
    id: 'piper-pa-28',
    name: 'Piper PA-28',
    cruiseSpeedKmh: 215,
    fuelBurnLitersPerHour: 36
  },
  {
    id: 'diamond-da40',
    name: 'Diamond DA40',
    cruiseSpeedKmh: 235,
    fuelBurnLitersPerHour: 28
  }
];

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
    availableFlightTimeMinutes: 120,
    aircraftId: 'cessna-172',
    cruiseSpeedKmh: 226,
    fuelBurnLitersPerHour: 34,
    fuelPricePerLiter: 2.3,
    preference: 'coast',
    safetyMarginPercent: 15
  };

  protected readonly airports = AIRPORT_OPTIONS;
  protected readonly aircraftOptions = AIRCRAFT_OPTIONS;
  protected readonly preferences = [
    { value: 'coast', label: 'Costa' },
    { value: 'mountain', label: 'Montana' },
    { value: 'short', label: 'Corta' },
    { value: 'scenic', label: 'Escenica' }
  ];

  protected recommendations: RecommendedRoute[] = [];
  protected selectedRouteIndex = 0;
  protected isLoading = false;
  protected errorMessage = '';

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
    this.selectedRouteIndex = 0;
    this.renderMap();

    this.recommendationService.recommend(this.form).subscribe({
      next: (response) => {
        this.recommendations = response.recommendations;
        this.selectedRouteIndex = 0;
        this.isLoading = false;
        this.renderMap();
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.resolveErrorMessage(error);
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

  protected routeTypeLabel(routeType: RouteType): string {
    switch (routeType) {
      case 'PREDEFINED':
        return 'Manual';
      case 'GENERATED_ONE_WAYPOINT':
        return 'Generada - 1 waypoint';
      case 'GENERATED_TWO_WAYPOINTS':
        return 'Generada - 2 waypoints';
    }
  }

  protected usefulFlightTimeMinutes(): number {
    return this.form.availableFlightTimeMinutes * (100 - this.form.safetyMarginPercent) / 100;
  }

  protected applyAircraftDefaults(): void {
    const aircraft = this.aircraftOptions.find((option) => option.id === this.form.aircraftId);

    if (!aircraft) {
      return;
    }

    this.form.cruiseSpeedKmh = aircraft.cruiseSpeedKmh;
    this.form.fuelBurnLitersPerHour = aircraft.fuelBurnLitersPerHour;
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

    this.recommendations.forEach((route, index) => {
      const isSelected = index === this.selectedRouteIndex;
      const points = this.routePoints(route, departureAirport);

      this.addRoutePolyline(route, points, isSelected);

      if (isSelected) {
        this.addWaypointMarkers(route);
      }
    });

    const selectedRoute = this.selectedRoute();
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
      color: isSelected ? '#d9480f' : '#2f80ed',
      weight: isSelected ? 5 : 3,
      opacity: isSelected ? 0.95 : 0.42,
      lineCap: 'round',
      lineJoin: 'round'
    })
      .bindPopup(route.name)
      .addTo(this.routesLayer);
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
