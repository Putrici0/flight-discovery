import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface RecommendationRequest {
  departureAirport: string;
  availableFlightTimeMinutes: number;
  aircraftId: string;
  cruiseSpeedKmh: number;
  fuelBurnLitersPerHour: number;
  fuelPricePerLiter: number | null;
  preference: string;
  safetyMarginPercent: number;
  plannedDepartureDateTime: string;
}

export interface Waypoint {
  name: string;
  latitude: number;
  longitude: number;
}

export type RouteType =
  | 'PREDEFINED'
  | 'GENERATED_ONE_WAYPOINT'
  | 'GENERATED_TWO_WAYPOINTS'
  | 'GENERATED_THREE_OR_MORE_WAYPOINTS';
export type RouteDurationCategory =
  | 'TOO_SHORT'
  | 'SHORT'
  | 'GOOD_FIT'
  | 'LONG'
  | 'SLIGHTLY_OVER_TIME'
  | 'TOO_LONG';

export interface RecommendedRoute {
  id: string;
  name: string;
  description: string;
  routeType?: RouteType;
  waypoints: Waypoint[];
  flightPath?: Waypoint[];
  sightseeingManeuvers?: SightseeingManeuver[];
  approximateDistanceKm: number;
  baseFlightTimeMinutes?: number;
  sightseeingTimeMinutes?: number;
  plannedDepartureDateTime?: string;
  sunAzimuthDegrees?: number;
  sunExposureScore?: number;
  sunExposureSummary?: string;
  estimatedTimeMinutes: number;
  estimatedTimeHours: number;
  routeDurationCategory: RouteDurationCategory;
  estimatedFuelLiters: number;
  fuelPricePerLiter: number;
  fuelPriceSource: 'MANUAL' | 'MOCK';
  estimatedCost: number;
  totalScore: number;
  weatherScore?: number;
  windKmh?: number;
  cloudCoverPercent?: number;
  precipitationProbability?: number;
  visibilityKm?: number;
  temperatureCelsius?: number;
  routeWeatherSummary?: RouteWeatherSummary;
  scoreBreakdown: {
    weatherScore: number;
    timeFitScore: number;
    preferenceScore: number;
    scenicScore: number;
    costScore: number;
    totalScore: number;
  };
  explanation: string;
  warnings?: string[];
}

export interface RouteWeatherSummary {
  averageWindKmh: number;
  maxWindKmh: number;
  averageCloudCoverPercent: number;
  maxPrecipitationProbability: number;
  minVisibilityKm: number;
  averageTemperatureCelsius: number;
}

export interface SightseeingManeuver {
  waypointName: string;
  maneuverType: string;
  minutes: number;
  radiusKm: number;
  instruction: string;
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
