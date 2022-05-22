package dachertanov.pal.palbackendservice.service;

import dachertanov.pal.palbackenddto.anime.AnimeOutDto;
import dachertanov.pal.palbackenddto.user.AnimeTypeDistributionOutDto;
import dachertanov.pal.palbackenddto.user.UserAnimeTimeDistributionOutDto;
import dachertanov.pal.palbackenddto.user.UserFavouriteGenresOutDto;
import dachertanov.pal.palbackenddto.user.UserStatisticOutDto;
import dachertanov.pal.palbackendservice.entity.Anime;
import dachertanov.pal.palbackendservice.entity.AnimeTag;
import dachertanov.pal.palbackendservice.entity.UserAnimeRecommendation;
import dachertanov.pal.palbackendservice.entity.UserFavouriteGenres;
import dachertanov.pal.palbackendservice.mapper.AnimeMapper;
import dachertanov.pal.palbackendservice.mapper.UserStatisticMapper;
import dachertanov.pal.palbackendservice.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class UserStatisticService {
    private final static String[] intToMonth = {"Январь", "Февраль", "Март",
            "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь",
            "Октябрь", "Ноябрь", "Декабрь"};

    private final UserStatisticMapper userStatisticMapper;
    private final UserStatisticRepository userStatisticRepository;
    private final UserAnimeActivityRepository userAnimeActivityRepository;
    private final AnimeRepository animeRepository;
    private final AnimeMapper animeMapper;
    private final UserFavouriteGenresRepository userFavouriteGenresRepository;
    private final AnimeTagRepository animeTagRepository;
    private final UserAnimeRecommendationRepository userAnimeRecommendationRepository;

    public Optional<UserStatisticOutDto> getUserStatistic(UUID userId) {
        return userStatisticRepository.findById(userId)
                .map(userStatisticMapper::entityToOutDto);
    }

    @Transactional
    public List<AnimeOutDto> getLastWatchedAnime(UUID userId, int animeCount) {
        Page<UUID> animeIds = userAnimeActivityRepository.findAllTopByUserIdEquals(userId,
                PageRequest.of(0, animeCount, Sort.by(Sort.Direction.DESC, "dateTimeWatched")));
        Page<Anime> lastWatchedAnime = animeRepository.findAllByAnimeIdIn(animeIds.getContent(),
                PageRequest.of(0, animeCount));

        return lastWatchedAnime.getContent().stream()
                .map(animeMapper::entityToOut)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<UserFavouriteGenresOutDto> getUserFavouriteGenres(UUID userId) {
        List<UserFavouriteGenres> userFavouriteGenres = userFavouriteGenresRepository
                .findAllByUserIdEqualsOrderByRecommendationMarkDesc(userId);
        Map<UUID, AnimeTag> animeTagsMap = animeTagRepository.findAll()
                .stream()
                .collect(Collectors.toMap(AnimeTag::getAnimeTagId, Function.identity()));

        List<UserFavouriteGenresOutDto> result = new ArrayList<>();
        for (int index = 1; index <= userFavouriteGenres.size(); ++index) {
            var userFavouriteGenre = userFavouriteGenres.get(index - 1);
            result.add(new UserFavouriteGenresOutDto(
                    userFavouriteGenre.getAnimeTagId(),
                    animeTagsMap.get(userFavouriteGenre.getAnimeTagId()).getTag(),
                    userFavouriteGenre.getRecommendationMark(),
                    index
            ));
        }

        return result;
    }

    @Transactional
    public List<AnimeTypeDistributionOutDto> getUserAnimeTypeDistribution(UUID userId) {
        List<UUID> allWatchedAnimeIds = userAnimeActivityRepository.findAllWatchedAnime(userId);
        return animeRepository.getUserAnimeTypeDistribution(allWatchedAnimeIds);
    }

    @Transactional
    public List<UserAnimeTimeDistributionOutDto> getUserAnimeTimeDistribution(UUID userId) {
        LocalDateTime nextStartMonth = LocalDateTime.now()
                .minusDays(LocalDateTime.now().getDayOfMonth() - 1)
                .plusMonths(1);

        List<UserAnimeTimeDistributionOutDto> result = new ArrayList<>();
        for (int i = 0; i < 12; ++i) {
            LocalDateTime handledDateTime = nextStartMonth.minusMonths(i);

            Long watchedAnimeCount = userAnimeActivityRepository.getAllWatchedAnimeBeforeDate(handledDateTime, userId);

            String monthValue = intToMonth[handledDateTime.minusMonths(1).getMonthValue() - 1];
            String yearValue = Integer.valueOf(handledDateTime.minusMonths(1).getYear()).toString();
            yearValue = yearValue.substring(yearValue.length() - 2);

            result.add(new UserAnimeTimeDistributionOutDto(monthValue + " " + yearValue, watchedAnimeCount));
        }

        Collections.reverse(result);
        return result;
    }

    @Transactional
    public void initFavouriteGenres(UUID userId) {
        List<AnimeTag> allAnimeTags = animeTagRepository.findAll();
        for (var animeTag : allAnimeTags) {
            userFavouriteGenresRepository.save(
                    new UserFavouriteGenres(userId, animeTag.getAnimeTagId(), 0.0));
        }
    }

    @Transactional
    public void initAnimeRecommendation(UUID userId) {
        List<Anime> allAnime = animeRepository.findAll();
        for (var anime : allAnime) {
            userAnimeRecommendationRepository.save(
                    new UserAnimeRecommendation(userId, anime.getAnimeId(), 0.0));
        }
    }
}
