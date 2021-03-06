package dachertanov.pal.palrecommendationservice.service;

import dachertanov.pal.palrecommendationdto.WatchedAnime;
import dachertanov.pal.palrecommendationservice.entity.*;
import dachertanov.pal.palrecommendationservice.repository.AnimeRatingRepository;
import dachertanov.pal.palrecommendationservice.repository.AnimeRepository;
import dachertanov.pal.palrecommendationservice.repository.UserAnimeRecommendationRepository;
import dachertanov.pal.palrecommendationservice.repository.UserFavouriteGenresRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class RecommendationService {
    private final UserAnimeRecommendationRepository userAnimeRecommendationRepository;
    private final AnimeRepository animeRepository;
    private final UserFavouriteGenresRepository userFavouriteGenresRepository;
    private final AnimeRatingRepository animeRatingRepository;

    @Transactional
    public void updateRecommendation(WatchedAnime watchedAnime) {
        Map<UUID, AnimeTagCntResponse> tagsPopularity =
                userAnimeRecommendationRepository.getUserAnimeTagsMarks(watchedAnime.getUserId())
                        .stream()
                        .collect(Collectors.toMap(AnimeTagCntResponse::getAnimeTagId, Function.identity()));
        log.info("Tags popularity: {}", tagsPopularity);

        updateUserFavouriteGenres(watchedAnime.getUserId(), tagsPopularity);
        updateAnimeRating(watchedAnime.getAnimeId());

        List<Anime> animeList = animeRepository.findAll();
        for (var anime : animeList) {
            double recommendationMark = 0;
            for (var tag : anime.getAnimeTags()) {
                if (tagsPopularity.containsKey(tag.getAnimeTagId())) {
                    recommendationMark += tagsPopularity.get(tag.getAnimeTagId()).getCnt();
                }
            }

            userAnimeRecommendationRepository.save(
                    new UserAnimeRecommendation(watchedAnime.getUserId(), anime.getAnimeId(), recommendationMark));
        }
    }

    private void updateUserFavouriteGenres(UUID userId, Map<UUID, AnimeTagCntResponse> tagsPopularity) {
        for (var tagId : tagsPopularity.keySet()) {
            AnimeTagCntResponse tagCntResponse = tagsPopularity.get(tagId);
            userFavouriteGenresRepository.save(new UserFavouriteGenres(userId, tagId, (double) tagCntResponse.getCnt()));
        }
    }

    private void updateAnimeRating(UUID animeId) {
        Optional<AnimeRating> opAnimeRating = animeRatingRepository.findById(animeId);

        if (opAnimeRating.isPresent()) {
            AnimeRating animeRating = opAnimeRating.get();
            animeRating.setWeekViews(animeRating.getWeekViews() + 1);
            animeRating.setMonthViews(animeRating.getMonthViews() + 1);
            animeRating.setYearViews(animeRating.getYearViews() + 1);

            animeRatingRepository.save(animeRating);
        }
    }
}
