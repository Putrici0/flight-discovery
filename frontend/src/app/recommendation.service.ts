import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface RecommendationRequest {
  departureAirport: string;
  availableFlightTimeMinutes: number;
  aircraftId: string;
  cruiseSpeedKmh: number;
  fuelBurnLitersPerHour: number;
  fuelPricePerLiter: number;
  preference: string;
}

export interface Waypoint {
  name: string;
  latitude: number;
  longitude: number;
}

export interface RecommendedRoute {
  id: string;
  name: string;
  description: string;
  waypoints: Waypoint[];
  approximateDistanceKm: number;
  estimatedTimeMinutes: number;
  estimatedTimeHours: number;
  estimatedFuelLiters: number;
  estimatedCost: number;
  totalScore: number;
  scoreBreakdown: {
    weatherScore: number;
    timeFitScore: number;
    preferenceScore: number;
    scenicScore: number;
    costScore: number;
    totalScore: number;
  };
  explanation: string;
  warnings: string[];
}

export interface RecommendationResponse {
  recommendations: RecommendedRoute[];
  warnings: string[];
}

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  constructor(private readonly http: HttpClient) {}

  recommend(request: RecommendationRequest): Observable<RecommendationResponse> {
    return this.http.post<RecommendationResponse>('/api/recommendations', request);
  }
}
