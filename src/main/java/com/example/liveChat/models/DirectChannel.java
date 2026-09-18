package com.example.liveChat.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "TB_DIRECT_CHANNEL", uniqueConstraints =
        @UniqueConstraint(columnNames = {"participant_one_id", "participant_two_id"}))
@Getter
@NoArgsConstructor
public class DirectChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_one_id", nullable = false)
    private User participantOne;

    @ManyToOne(optional = false)
    @JoinColumn(name = "participant_two_id", nullable = false)
    private User participantTwo;

    private Instant createdAt;

    public DirectChannel(User participantOne, User participantTwo) {
        this.participantOne = participantOne;
        this.participantTwo = participantTwo;
        this.createdAt = Instant.now();
    }

    public boolean hasParticipant(String userId) {
        return participantOne.getId().equals(userId) || participantTwo.getId().equals(userId);
    }

    public User otherParticipant(String userId) {
        return participantOne.getId().equals(userId) ? participantTwo : participantOne;
    }
}
