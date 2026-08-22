package flightdiscovery.paull.api.recommendation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import flightdiscovery.paull.application.recommendation.RecommendationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(request);
    }

    @PostMapping("/debug")
    public RecommendationDevelopmentDebugResponse debug(@Valid @RequestBody RecommendationRequest request) {
        return recommendationService.debug(request);
    }
}
