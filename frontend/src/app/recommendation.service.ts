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
  weatherProvider: 'open-meteo' | 'mock';
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
  visualOrientationScore?: number;
  orientationScore?: number;
  predominantSunPosition?: SunPosition;
  recommendedViewingSide?: ViewingSide;
  orientationFavorableReason?: string;
  frontalSunLegs?: string[];
  legOrientations?: RouteLegOrientation[];
  estimatedTimeMinutes: number;
  estimatedTimeHours: number;
  routeDurationCategory: RouteDurationCategory;
  estimatedFuelLiters: number;
  fuelPricePerLiter: number;
  fuelPriceSource: 'MANUAL' | 'MOCK';
  fuelPriceIsMock: boolean;
  fuelTypeUsed: string;
  fuelPriceAirportCode?: string;
  estimatedCost: number;
  totalScore: number;
  weatherScore?: number;
  weatherProvider?: string;
  weatherIsMock?: boolean;
  windKmh?: number;
  cloudCoverPercent?: number;
  precipitationProbability?: number;
  visibilityKm?: number;
  temperatureCelsius?: number;
  routeWeatherSummary?: RouteWeatherSummary;
  aviationWeather?: RouteAviationWeatherSummary;
  scoreBreakdown: {
    weatherScore: number;
    timeFitScore: number;
    preferenceScore: number;
    scenicScore: number;
    visualOrientationScore?: number;
    costScore: number;
    totalScore: number;
  };
  explanation: string;
  warnings?: string[];
}

export type SunPosition = 'FRONT' | 'BEHIND' | 'LEFT' | 'RIGHT' | 'LOW_LIGHT' | 'UNKNOWN';
export type ViewingSide = 'LEFT' | 'RIGHT' | 'FRONT' | 'BEHIND' | 'UNKNOWN';

export interface RouteLegOrientation {
  fromName: string;
  toName: string;
  distanceKm: number;
  aircraftBearingDegrees: number;
  sunAzimuthDegrees: number;
  relativeSunAngleDegrees: number;
  sunPosition: SunPosition;
  frontalSunPenalty: number;
  recommendedViewingSide: ViewingSide;
  viewingQualityScore: number;
  explanation: string;
}

export interface RouteWeatherSummary {
  averageWindKmh: number;
  maxWindKmh: number;
  averageCloudCoverPercent: number;
  maxPrecipitationProbability: number;
  minVisibilityKm: number;
  averageTemperatureCelsius: number;
  weatherScore: number;
  provider: string;
  isMock: boolean;
}

export interface RouteAviationWeatherSummary {
  provider: string;
  sourceUrl: string;
  operationalUseAllowed: boolean;
  airports: AirportAviationWeather[];
  warnings: string[];
}

export interface AirportAviationWeather {
  airportCode: string;
  airportName: string;
  latitude: number;
  longitude: number;
  distanceFromRouteKm: number;
  metar?: MetarReport | null;
  taf?: TafReport | null;
  tafAvailable: boolean;
  warnings: string[];
}

export interface MetarReport {
  airportCode: string;
  stationName?: string;
  rawText: string;
  observedAt?: string;
  receivedAt?: string;
  ageMinutes?: number;
  flightCategory?: string;
  windDirectionDegrees?: number | string;
  windSpeedKt?: number;
  windGustKt?: number;
  visibilityStatuteMiles?: string;
  altimeterHpa?: number;
  temperatureCelsius?: number;
  dewpointCelsius?: number;
  weather?: string;
  clouds: AviationCloudLayer[];
  source: string;
}

export interface TafReport {
  airportCode: string;
  stationName?: string;
  rawText: string;
  issuedAt?: string;
  bulletinAt?: string;
  validFrom?: string;
  validTo?: string;
  ageMinutes?: number;
  forecastPeriods: TafForecastPeriod[];
  source: string;
}

export interface TafForecastPeriod {
  from?: string;
  to?: string;
  becomingAt?: string;
  change?: string;
  probability?: number;
  windDirectionDegrees?: number | string;
  windSpeedKt?: number;
  windGustKt?: number;
  visibilityStatuteMiles?: string;
  weather?: string;
  clouds: AviationCloudLayer[];
}

export interface AviationCloudLayer {
  cover?: string;
  baseFeet?: number;
  type?: string;
}

export interface SightseeingManeuver {
  waypointName: string;
  maneuverType: string;
  minutes: number;
  radiusKm: number;
  sunAzimuthDegrees?: number;
  preferredViewingBearingDegrees?: number;
  orbitPath?: Waypoint[];
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
  aviationWeatherAirports?: number;
  aviationWeatherWarnings?: number;
}

export interface OpenMeteoForecastResponse {
  latitude: number;
  longitude: number;
  hourly: {
    time: string[];
    temperature_2m?: number[];
    wind_speed_10m?: number[];
    wind_direction_10m?: number[];
    cloud_cover?: number[];
    precipitation_probability?: number[];
    visibility?: number[];
  };
}

export interface RainViewerManifest {
  host: string;
  generated: number;
  radar?: {
    past?: RainViewerFrame[];
    nowcast?: RainViewerFrame[];
  };
}

export interface RainViewerFrame {
  time: number;
  path: string;
}

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  constructor(private readonly http: HttpClient) {}

  recommend(request: RecommendationRequest): Observable<RecommendationResponse> {
    return this.http.post<RecommendationResponse>('/api/recommendations', request);
  }

  weatherForecast(
    latitudes: number[],
    longitudes: number[],
    startDate: string,
    endDate: string
  ): Observable<OpenMeteoForecastResponse | OpenMeteoForecastResponse[]> {
    const latitude = latitudes.map((value) => value.toFixed(4)).join(',');
    const longitude = longitudes.map((value) => value.toFixed(4)).join(',');
    const hourly = [
      'temperature_2m',
      'wind_speed_10m',
      'wind_direction_10m',
      'cloud_cover',
      'precipitation_probability',
      'visibility'
    ].join(',');

    return this.http.get<OpenMeteoForecastResponse | OpenMeteoForecastResponse[]>(
      'https://api.open-meteo.com/v1/forecast',
      {
        params: {
          latitude,
          longitude,
          hourly,
          wind_speed_unit: 'kmh',
          timezone: 'auto',
          start_date: startDate,
          end_date: endDate
        }
      }
    );
  }

  rainViewerManifest(): Observable<RainViewerManifest> {
    return this.http.get<RainViewerManifest>('https://api.rainviewer.com/public/weather-maps.json');
  }
}
