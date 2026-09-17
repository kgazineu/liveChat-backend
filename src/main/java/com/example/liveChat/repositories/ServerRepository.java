package com.example.liveChat.repositories;

import com.example.liveChat.models.Server;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerRepository extends JpaRepository<Server, String> {
}
