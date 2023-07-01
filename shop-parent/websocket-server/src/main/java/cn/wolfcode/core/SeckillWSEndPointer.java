package cn.wolfcode.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint("/seckill/{token}")
public class SeckillWSEndPointer {

    public static final Map<String, Session> SESSION_MAP = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(@PathParam("token") String token, Session session) throws IOException {
        // 当客户端建立连接成功时, 会执行该方法1
        log.info("[Server] 新的秒杀请求, 客户端={}", token);
        SESSION_MAP.put(token, session);
    }

    @OnClose
    public void onClose(@PathParam("token") String token) {
        // 当客户端建立连接成功时, 会执行该方法
        log.info("[Server] 客户端 {} 关闭了连接...", token);
        SESSION_MAP.remove(token);
    }

    @OnError
    public void onError(@PathParam("token") String token, Throwable throwable) {
        // 当客户端建立连接成功时, 会执行该方法
        log.info("[Server] 客户端连接出现异常...", throwable);
        SESSION_MAP.remove(token);
    }
}
