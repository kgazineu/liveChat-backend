package com.example.liveChat.repositories;

import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship,Long> {
    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user1 AND f.addressee = :user2) OR (f.requester = :user2 AND f.addressee = :user1)")
    Optional<Friendship> findRelationship(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT f FROM Friendship f WHERE f.addressee = :addressee AND f.status = :status " +
            "AND f.requester.deletedAt IS NULL")
    Page<Friendship> findPendingRequests(@Param("addressee") User addressee,
                                         @Param("status") FriendshipStatus status,
                                         Pageable pageable);

    @Query("SELECT f FROM Friendship f WHERE (f.requester = :user OR f.addressee = :user) " +
            "AND f.status = 'ACCEPTED' AND f.requester.deletedAt IS NULL AND f.addressee.deletedAt IS NULL")
    Page<Friendship> findAllFriends(@Param("user") User user, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM Friendship f " +
            "WHERE ((f.requester = :user1 AND f.addressee = :user2) OR " +
            "(f.requester = :user2 AND f.addressee = :user1)) AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("user1") User user1, @Param("user2") User user2);

    @Modifying
    @Query("delete from Friendship friendship where friendship.requester.id = :userId " +
            "or friendship.addressee.id = :userId")
    void deleteAllInvolvingUser(@Param("userId") String userId);

}
