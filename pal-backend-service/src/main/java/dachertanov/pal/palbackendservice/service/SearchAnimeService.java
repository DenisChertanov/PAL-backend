package dachertanov.pal.palbackendservice.service;

import dachertanov.pal.palbackenddto.anime.AnimePageOutDto;
import dachertanov.pal.palbackenddto.search.in.AppliedFilters;
import dachertanov.pal.palbackenddto.search.out.FilterObject;
import dachertanov.pal.palbackendservice.entity.*;
import dachertanov.pal.palbackendservice.mapper.AnimeMapper;
import dachertanov.pal.palbackendservice.mapper.AnimePageMapper;
import dachertanov.pal.palbackendservice.mapper.SearchAnimeMapper;
import dachertanov.pal.palbackendservice.repository.*;
import dachertanov.pal.palbackendservice.security.config.SecurityUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class SearchAnimeService {
    private final AnimeTagRepository animeTagRepository;
    private final AnimeStateRepository animeStateRepository;
    private final AnimeTypeRepository animeTypeRepository;
    private final AnimeSortRepository animeSortRepository;
    private final SearchAnimeMapper searchAnimeMapper;
    private final AnimeMapper animeMapper;
    private final AnimeRepository animeRepository;
    private final AnimePageMapper animePageMapper;
    private final UserAnimeActivityRepository userAnimeActivityRepository;

    public FilterObject getFilterObject() {
        List<AnimeTag> animeTags = animeTagRepository.findAll();
        List<AnimeState> animeStates = animeStateRepository.findAll();
        List<AnimeType> animeTypes = animeTypeRepository.findAll();
        List<AnimeSort> animeSorts = animeSortRepository.findAll();

        return searchAnimeMapper.filterObjectFromFilters(animeTags, animeTypes, animeStates, animeSorts);
    }

    public Optional<AnimePageOutDto> searchByAppliedFilters(AppliedFilters appliedFilters) {
        // ???????????? ?????????? ?????????? ???????????????????? ???? ???????????????????? ????????????????, ???????????????????? ??????????, ?????????? ???? ?? ????, ???????????????? ??????????
        List<Anime> firstAnimeList = animeRepository.searchByAppliedFilters(
                appliedFilters.getFilter().getIncludeStates(), appliedFilters.getFilter().getIncludeStates().size(),
                appliedFilters.getFilter().getIncludeTypes(), appliedFilters.getFilter().getIncludeTypes().size(),
                appliedFilters.getFilter().getYearFrom(),
                appliedFilters.getFilter().getYearTo(),
                appliedFilters.getFilter().getNamePrefix());

        // ???????????? ?????????? ?????????? ???????????????????? ???? ???????????????????? ???????????? + ?????????????????? ???????????????????? ????????????????
        List<Anime> secondAnimeList = appliedFilters.getFilter().getIncludeGenres().size() == 0
                ? firstAnimeList
                : animeRepository.searchByIncludeGenres(
                firstAnimeList.stream()
                        .map(Anime::getAnimeId)
                        .collect(Collectors.toList()),
                appliedFilters.getFilter().getIncludeGenres(),
                (long) appliedFilters.getFilter().getIncludeGenres().size());

        // ???????????? ??????????, ?????????????? ???????? ?????????????????? ?????????? ???????????????????? ???? ?????????????????????? ???????????? + ?????????????????? ???????????????????? ????????????????
        List<Anime> thirdAnimeList = appliedFilters.getFilter().getExcludeGenres().size() == 0
                ? new ArrayList<>()
                : animeRepository.searchByExcludeGenres(
                secondAnimeList.stream()
                        .map(Anime::getAnimeId)
                        .collect(Collectors.toList()),
                appliedFilters.getFilter().getExcludeGenres());

        // ???????????? id ??????????, ?????????????? ?????????? ?????????????????? ???? ??????????????????????
        List<UUID> excludeIds = thirdAnimeList.stream()
                .map(Anime::getAnimeId)
                .collect(Collectors.toList());

        // ?????????????????? ???? ?????????????????????? id ????-???? ???????????????????? ????????????
        List<UUID> fourthAnimeList = secondAnimeList.stream()
                .map(Anime::getAnimeId)
                .filter(animeId -> !excludeIds.contains(animeId))
                .collect(Collectors.toList());

        // id ?????????? ?????????? ???????????????????? ???????????????? + ???????????????????? ????????????????????????????
        List<UUID> fifthAnimeList = fourthAnimeList;
        if (appliedFilters.getFilter().isExcludeWatched()) {
            fifthAnimeList = animeListAfterExcludeWatched(fourthAnimeList);
        }

        // id ?????????? ?????????? ???????????????????? ?????????????? "?????????????????????? ??????????????"
        List<UUID> sixthAnimeList = animeIdsAfterApplyWhoWatched(fifthAnimeList, appliedFilters.getFilter().getWatchedByUsers());

        UUID userId = SecurityUtil.getCurrentUserId();
        Page<Anime> result = animeRepository.findAllByAnimeIdInJoinUserRecommendation(
                sixthAnimeList,
                userId,
                PageRequest.of(appliedFilters.getPage().getPageNumber(),
                        appliedFilters.getPage().getPageSize(),
                        getSortById(appliedFilters.getFilter().getSortsBy())));

        return Optional.of(animePageMapper.outDtoFromPage(result));
    }

    private List<UUID> animeListAfterExcludeWatched(List<UUID> animeList) {
        UUID userId;
        List<UUID> result = animeList;

        try {
            userId = SecurityUtil.getCurrentUserId();

            List<UUID> allWatchedAnimeIds = userAnimeActivityRepository.findAllWatchedAnime(userId);

            if (!allWatchedAnimeIds.isEmpty()) {
                result = animeRepository.notWatchedAnimeInIds(animeList, allWatchedAnimeIds);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return result;
    }

    private Sort getSortById(UUID sortId) {
        if (sortId == null) {
            return Sort.unsorted();
        }

        Optional<AnimeSort> opAnimeSort = animeSortRepository.findById(sortId);

        if (opAnimeSort.isPresent()) {
            AnimeSort animeSort = opAnimeSort.get();
            String sortName = animeSort.getSort();
            Sort sort;

            switch (sortName) {
                case "????????????????":
                    sort = Sort.by(Sort.Direction.DESC, "mark");
                    break;
                case "?????????????? ????????????":
                    sort = Sort.by(Sort.Direction.DESC, "userAnimeRecommendation.recommendationMark", "mark");
                    break;
                case "???????? ????????????":
                    sort = Sort.by(Sort.Direction.DESC, "year");
                    break;
                case "???????????????? ???? ?? ???? ??":
                    sort = Sort.by(Sort.Direction.ASC, "title");
                    break;
                case "???????????????? ???? ?? ???? ??":
                    sort = Sort.by(Sort.Direction.DESC, "title");
                    break;
                default:
                    sort = Sort.unsorted();
                    break;
            }

            return sort;
        }
        return Sort.unsorted();
    }

    private List<UUID> animeIdsAfterApplyWhoWatched(List<UUID> animeIds, List<UUID> watchedByUsers) {
        return watchedByUsers.isEmpty()
                ? animeIds
                : animeRepository.findAllByAnimeIdsInAndWatchedByUsers(animeIds, watchedByUsers, (long) watchedByUsers.size());
    }
}
