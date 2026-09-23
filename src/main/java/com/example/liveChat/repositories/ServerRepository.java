package com.example.liveChat.repositories;

import com.example.liveChat.models.Server;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServerRepository extends JpaRepository<Server, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select server from Server server where server.owner.id = :ownerId order by server.id asc")
    List<Server> findOwnedByUserForUpdate(@Param("ownerId") String ownerId);
}
