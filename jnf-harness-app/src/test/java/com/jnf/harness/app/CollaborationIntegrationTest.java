package com.jnf.harness.app;

import com.jnf.harness.domain.collaboration.CollaborationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 协作房间验收（第 13b 节 DoD）：建房/入群/消息事件 + seq 续传语义。
 */
@SpringBootTest(classes = JnfHarnessApplication.class,
        properties = {"spring.datasource.url=jdbc:h2:mem:collabtest;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"})
class CollaborationIntegrationTest {

    @Autowired
    CollaborationService collaborationService;

    @Test
    @DisplayName("房间事件溯源：seq 单调 + afterSeq 续传")
    void room_events_sequencing_and_resume() {
        CollaborationService.Room room = collaborationService.createRoom("ws-1", "联调房间", "验证事件流");
        collaborationService.join(room.id(), "dh-1", "member");
        collaborationService.join(room.id(), "dh-2", "member");
        collaborationService.postMessage(room.id(), "user", "大家好");
        collaborationService.postMessage(room.id(), "dh-1", "收到，开工");

        List<CollaborationService.RoomEvent> all = collaborationService.eventsAfter(room.id(), 0);
        // 事件：ROOM_CREATED + PARTICIPANT_JOINED x2 + MESSAGE_CREATED x2 = 5
        assertEquals(5, all.size());
        for (int i = 0; i < all.size(); i++) {
            assertEquals(i + 1, all.get(i).seq(), "seq 必须单调连续");
        }

        // 续传语义：afterSeq=3 → 只拿 4、5
        List<CollaborationService.RoomEvent> tail = collaborationService.eventsAfter(room.id(), 3);
        assertEquals(2, tail.size());
        assertEquals(4, tail.get(0).seq());
        assertTrue(tail.get(1).payload().contains("开工"));

        assertEquals(2, collaborationService.participants(room.id()).size());
    }
}
