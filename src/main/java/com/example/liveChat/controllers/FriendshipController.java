package com.example.liveChat.controllers;

import com.example.liveChat.dto.FriendshipRequestDTO;
import com.example.liveChat.dto.FriendshipResponseDTO;
import com.example.liveChat.dto.UserResponseDTO;
import com.example.liveChat.models.User;
import com.example.liveChat.services.FriendshipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/friendships")
public class FriendshipController {

    @Autowired
    private FriendshipService friendshipService;

    @PostMapping("/send")
    public ResponseEntity<Void> sendFriendRequest(
            @RequestBody FriendshipRequestDTO body,
            @AuthenticationPrincipal User loggedUser
    ) {
        friendshipService.sendFriendRequest(loggedUser.getId(), body.targetUserId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/accept")
    public ResponseEntity<Void> acceptRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal User loggedUser
    ) {
        friendshipService.acceptFriendRequest(id, loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal User loggedUser
    ) {
        friendshipService.rejectFriendRequest(id, loggedUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getMyFriends(@AuthenticationPrincipal User loggedUser) {
        var friends = friendshipService.getUserFriends(loggedUser.getId());
        var response = friends.stream()
                .map(UserResponseDTO::forRegister)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendshipResponseDTO>> getPendingRequests(@AuthenticationPrincipal User loggedUser) {
        var requests = friendshipService.getPendingRequests(loggedUser.getId());
        var response = requests.stream()
                .map(request -> new FriendshipResponseDTO(
                        request.getId(),
                        request.getRequester().getName(),
                        request.getRequester().getId()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

}
