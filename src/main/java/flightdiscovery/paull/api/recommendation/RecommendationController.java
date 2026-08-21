package flightdiscovery.paull.api.recommendation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import flightdiscovery.paull.application.recommendation.RouteRecommendationService;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RouteRecommendationService recommendationService;

    public RecommendationController(RouteRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request) {
        return recommendationService.recommend(request);
    }
}
