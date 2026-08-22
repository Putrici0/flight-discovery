import { AfterViewInit, Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DecimalPipe, NgFor, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import * as L from 'leaflet';

import {
  RecommendationRequest,
  RecommendationService,
  RecommendedRoute,
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
    preference: 'coast'
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

    const bounds = L.latLngBounds([]);
    const departureAirport = this.departureAirport();

    if (departureAirport) {
      this.addAirportMarker(departureAirport, bounds);
    }

    const selectedBounds = L.latLngBounds([]);

    this.recommendations.forEach((route, index) => {
      const points = this.routePoints(route, departureAirport);
      const isSelected = index === this.selectedRouteIndex;

      if (points.length > 1) {
        L.polyline(points, {
          color: isSelected ? '#d9480f' : '#2f80ed',
          weight: isSelected ? 5 : 3,
          opacity: isSelected ? 0.95 : 0.42
        })
          .bindPopup(route.name)
          .addTo(this.routesLayer);
      }

      points.forEach((point) => {
        bounds.extend(point);
        if (isSelected) {
          selectedBounds.extend(point);
        }
      });

      route.waypoints.forEach((waypoint) => {
        L.marker([waypoint.latitude, waypoint.longitude], {
          icon: this.waypointIcon
        })
          .bindPopup(`${waypoint.name}<br>${route.name}`)
          .addTo(this.routesLayer);
      });
    });

    const visibleBounds = selectedBounds.isValid() ? selectedBounds : bounds;

    if (visibleBounds.isValid()) {
      this.map.fitBounds(visibleBounds, {
        padding: [28, 28],
        maxZoom: 10
      });
    }

    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  private departureAirport(): AirportLocation | undefined {
    return AIRPORTS[this.form.departureAirport.trim().toUpperCase()];
  }

  private routePoints(route: RecommendedRoute, departureAirport?: AirportLocation): L.LatLngExpression[] {
    const points: L.LatLngExpression[] = [];

    if (departureAirport) {
      points.push([departureAirport.latitude, departureAirport.longitude]);
    }

    points.push(...route.waypoints.map((waypoint: Waypoint) => [waypoint.latitude, waypoint.longitude] as L.LatLngExpression));

    if (departureAirport && route.waypoints.length > 0) {
      points.push([departureAirport.latitude, departureAirport.longitude]);
    }

    return points;
  }

  private addAirportMarker(airport: AirportLocation, bounds: L.LatLngBounds): void {
    bounds.extend([airport.latitude, airport.longitude]);
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
