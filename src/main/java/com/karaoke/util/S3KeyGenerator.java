package com.karaoke.util;

import com.karaoke.model.enums.MediaType;
import com.karaoke.model.enums.StorageEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
public class S3KeyGenerator {

    public String generateKey(StorageEnvironment env, Long ownerId, String ownerType,
                              Long quizId, Long questionId, MediaType mediaType, String extension) {
        
        if (env == null || ownerId == null || mediaType == null) {
            throw new IllegalArgumentException("Environment, ownerId, and mediaType are required");
        }

        String hashPrefix = computeHashPrefix(ownerId);
        String safeOwnerType = StringUtils.hasText(ownerType) ? ownerType : "user";
        String safeQuizId = (quizId != null) ? "quiz_" + quizId : "unassigned";
        String safeQuestionId = (questionId != null) ? "q_" + questionId : "temp";
        String uuid = UUID.randomUUID().toString();
        
        String safeExtension = "";
        if (StringUtils.hasText(extension)) {
            safeExtension = extension.startsWith(".") ? extension : "." + extension;
        }

        return String.format("%s/%s/%s/%s/%s/%s/%s/%s%s",
                env.getPathValue(),
                hashPrefix,
                safeOwnerType,
                ownerId,
                safeQuizId,
                safeQuestionId,
                mediaType.name().toLowerCase(),
                uuid,
                safeExtension.toLowerCase());
    }

    public String computeHashPrefix(Long ownerId) {
        if (ownerId == null) {
            return "00";
        }
        int hash = Math.abs(ownerId.hashCode());
        int mod = hash % 256;
        return String.format("%02x", mod);
    }
}
