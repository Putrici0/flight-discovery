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
  safetyMarginPercent: number;
}

export interface Waypoint {
  name: string;
  latitude: number;
  longitude: number;
}

export type RouteType = 'PREDEFINED' | 'GENERATED_ONE_WAYPOINT' | 'GENERATED_TWO_WAYPOINTS';

export interface RecommendedRoute {
  id: string;
  name: string;
  description: string;
  routeType?: RouteType;
  waypoints: Waypoint[];
  approximateDistanceKm: number;
  estimatedTimeMinutes: number;
  estimatedTimeHours: number;
  estimatedFuelLiters: number;
  estimatedCost: number;
  totalScore: number;
  weatherScore?: number;
  windKmh?: number;
  cloudCoverPercent?: number;
  precipitationProbability?: number;
  visibilityKm?: number;
  scoreBreakdown: {
    weatherScore: number;
    timeFitScore: number;
    preferenceScore?: number;
    scenicScore: number;
    costScore: number;
    totalScore: number;
  };
  explanation: string;
  warnings?: string[];
}

export interface RecommendationResponse {
  recommendations: RecommendedRoute[];
  warnings: string[];
  debugInfo?: RecommendationDebugInfo;
}

export interface RecommendationDebugInfo {
  generatedCandidateRoutes: number;
  discardedByTimeRoutes: number;
  recommendedRoutes: number;
}

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  constructor(private readonly http: HttpClient) {}

  recommend(request: RecommendationRequest): Observable<RecommendationResponse> {
    return this.http.post<RecommendationResponse>('/api/recommendations', request);
  }
}
