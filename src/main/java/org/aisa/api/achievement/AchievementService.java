package org.aisa.api.achievement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.aisa.api.common.NotFoundException;
import org.aisa.api.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AchievementService {

    private final AchievementRepository achievements;
    private final MediaService media;

    public AchievementService(AchievementRepository achievements, MediaService media) {
        this.achievements = achievements;
        this.media = media;
    }

    public record AchievementResponse(
            UUID id,
            String title,
            String student,
            String category,
            LocalDate achievedOn,
            String description,
            String photoUrl) {}

    public record AchievementRequest(
            @NotBlank(message = "Title is required") @Size(max = 200) String title,
            @NotBlank(message = "Student name is required") @Size(max = 160) String student,
            @Size(max = 64) String category,
            LocalDate achievedOn,
            String description,
            String photoUrl,
            String photoPublicId) {}

    @Transactional(readOnly = true)
    public List<AchievementResponse> findAll(String category) {
        List<Achievement> found = (category == null || category.isBlank() || "all".equalsIgnoreCase(category))
                ? achievements.findAllNewestFirst()
                : achievements.findByCategoryNewestFirst(category);
        return found.stream().map(AchievementService::toResponse).toList();
    }

    @Transactional
    public AchievementResponse create(AchievementRequest request) {
        Achievement achievement = new Achievement(request.title().trim(), request.student().trim());
        apply(achievement, request);
        return toResponse(achievements.save(achievement));
    }

    @Transactional
    public AchievementResponse update(UUID id, AchievementRequest request) {
        Achievement achievement = require(id);
        if (achievement.getPhotoPublicId() != null
                && !achievement.getPhotoPublicId().equals(request.photoPublicId())) {
            media.deleteQuietly(achievement.getPhotoPublicId());
        }
        achievement.setTitle(request.title().trim());
        achievement.setStudent(request.student().trim());
        apply(achievement, request);
        return toResponse(achievements.save(achievement));
    }

    @Transactional
    public void delete(UUID id) {
        Achievement achievement = require(id);
        media.deleteQuietly(achievement.getPhotoPublicId());
        achievements.delete(achievement);
    }

    private Achievement require(UUID id) {
        return achievements.findById(id).orElseThrow(() -> new NotFoundException("Achievement", id));
    }

    private void apply(Achievement achievement, AchievementRequest request) {
        achievement.setCategory(blankToNull(request.category()));
        achievement.setAchievedOn(request.achievedOn());
        achievement.setDescription(blankToNull(request.description()));
        achievement.setPhotoUrl(blankToNull(request.photoUrl()));
        achievement.setPhotoPublicId(blankToNull(request.photoPublicId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AchievementResponse toResponse(Achievement a) {
        return new AchievementResponse(
                a.getId(),
                a.getTitle(),
                a.getStudent(),
                a.getCategory(),
                a.getAchievedOn(),
                a.getDescription(),
                a.getPhotoUrl());
    }
}
