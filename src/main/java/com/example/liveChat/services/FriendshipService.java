package com.example.liveChat.services;

import com.example.liveChat.models.Friendship;
import com.example.liveChat.models.FriendshipStatus;
import com.example.liveChat.models.User;
import com.example.liveChat.repositories.FriendshipRepository;
import com.example.liveChat.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FriendshipService {
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private UserRepository userRepository;


    @Transactional
    public void sendFriendRequest(String requesterId, String addresseeId) {
        if(requesterId.equals(addresseeId)) {
            throw new RuntimeException("User cant send a request to himself");
        }

        User requester = userRepository.findById(requesterId).orElseThrow(() -> new RuntimeException("User not found"));
        User addressee = userRepository.findById(addresseeId).orElseThrow(() -> new RuntimeException("User not found"));

        Optional<Friendship> existingRelationship = friendshipRepository.findRelationship(requester, addressee);

        if (existingRelationship.isPresent()) {
            Friendship friendship = existingRelationship.get();

            if (friendship.getStatus() == FriendshipStatus.ACCEPTED || friendship.getStatus() == FriendshipStatus.PENDING) {
                throw new RuntimeException("Friend request cannot be sent to an existing relation.");
            }

            if (friendship.getStatus() == FriendshipStatus.REJECTED) {
                friendship.setStatus(FriendshipStatus.PENDING);
                friendship.setCreatedAt(LocalDateTime.now());

                friendship.setRequester(requester);
                friendship.setAddressee(addressee);

                friendshipRepository.save(friendship);
                return;
            }
        }

        Friendship newFriendship = new Friendship(requester, addressee);
        friendshipRepository.save(newFriendship);
    }

    @Transactional
    public void rejectFriendRequest(Long friendshipId, String userIdDoLogado) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friend request not fount"));

        if (!friendship.getAddressee().getId().equals(userIdDoLogado)) {
            throw new RuntimeException("User not authorized to reject this friend request");
        }

        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
            throw new RuntimeException("Users are already friends");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    @Transactional
    public void acceptFriendRequest(Long friendshipId, String loggedUserId) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new RuntimeException("Friendship not found"));

        if(!friendship.getAddressee().getId().equals(loggedUserId)) {
            throw new RuntimeException("Only the addressee can accept the friend request");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
    }

    public List<User> getUserFriends(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<Friendship> friendships = friendshipRepository.findAllFriends(user);

        return friendships.stream()
                .map(f -> f.getRequester().equals(user) ? f.getAddressee() : f.getRequester())
                .toList();
    }

    public List<Friendship> getPendingRequests(String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        return friendshipRepository.findByAddresseeAndStatus(user, FriendshipStatus.PENDING);
    }

}
