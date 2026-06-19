package com.churnguard.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "model_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelVersion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String version;

    @Column(name = "trained_at", nullable = false)
    private Instant trainedAt;

    @Column(name = "auc_score")
    private Double aucScore;

    @Column(name = "precision_score")
    private Double precisionScore;

    @Column(name = "recall_score")
    private Double recallScore;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(columnDefinition = "TEXT")
    private String notes;
}