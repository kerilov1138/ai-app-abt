import express from "express";
import http from "http";
import path from "path";
import { WebSocketServer, WebSocket } from "ws";
import { createServer as createViteServer } from "vite";

interface ClientConnection {
  ws: WebSocket;
  room: string;
  role: "bebek" | "ebeveyn" | null;
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  // Create an HTTP server to share with Express and WebSockets
  const server = http.createServer(app);

  // Initialize WebSocket Server on the same server instance
  const wss = new WebSocketServer({ noServer: true });

  // Store active client connections
  const clients = new Map<WebSocket, ClientConnection>();

  wss.on("connection", (ws) => {
    // Initial mapping
    clients.set(ws, { ws, room: "", role: null });

    ws.on("message", (message) => {
      try {
        const data = JSON.parse(message.toString());
        const client = clients.get(ws);
        if (!client) return;

        switch (data.type) {
          case "join": {
            const { room, role } = data;
            if (!room || !role) return;

            // Clean up existing room stats if rejoining
            client.room = room;
            client.role = role;

            console.log(`[WS] Cihaz katıldı: Oda = ${room}, Rol = ${role}`);

            // Notify everyone in the room about the new participant
            broadcastToRoom(room, ws, {
              type: "partner-status",
              event: "connected",
              role: role,
            });

            // If an ebeveyn joined, notify them if a bebek is already present in this room
            if (role === "ebeveyn") {
              const bebekExists = Array.from(clients.values()).some(
                (c) => c.room === room && c.role === "bebek" && c.ws !== ws
              );
              ws.send(
                JSON.stringify({
                  type: "partner-status",
                  event: bebekExists ? "connected" : "disconnected",
                  role: "bebek",
                })
              );
            } else if (role === "bebek") {
              // If a bebek joined, notify them if an ebeveyn is already present
              const ebeveynExists = Array.from(clients.values()).some(
                (c) => c.room === room && c.role === "ebeveyn" && c.ws !== ws
              );
              ws.send(
                JSON.stringify({
                  type: "partner-status",
                  event: ebeveynExists ? "connected" : "disconnected",
                  role: "ebeveyn",
                })
              );
            }
            break;
          }

          case "audio-chunk": {
            // Relay audio chunk to all ebeveyn units in the same room
            if (client.room && client.role === "bebek") {
              broadcastToRoom(client.room, ws, {
                type: "audio-chunk",
                audio: data.audio, // Base64 or array
              });
            }
            break;
          }

          case "cry-detected": {
            // Relay cry alert to ebeveyn units
            if (client.room && client.role === "bebek") {
              broadcastToRoom(client.room, ws, {
                type: "cry-detected",
                level: data.level,
                duration: data.duration,
              });
            }
            break;
          }

          case "status-update": {
            // Relay volume/battery level updates to ebeveyn units
            if (client.room && client.role === "bebek") {
              broadcastToRoom(client.room, ws, {
                type: "status-update",
                volume: data.volume,
                battery: data.battery,
              });
            }
            break;
          }

          case "ping": {
            ws.send(JSON.stringify({ type: "pong" }));
            break;
          }

          default:
            break;
        }
      } catch (err) {
        console.error("WebSocket message parsing error:", err);
      }
    });

    ws.on("close", () => {
      const client = clients.get(ws);
      if (client) {
        console.log(`[WS] Cihaz ayrıldı: Oda = ${client.room}, Rol = ${client.role}`);
        if (client.room && client.role) {
          // Notify the other side that this client went offline
          broadcastToRoom(client.room, ws, {
            type: "partner-status",
            event: "disconnected",
            role: client.role,
          });
        }
        clients.delete(ws);
      }
    });
  });

  // Helper function to broadcast message to all other clients in the same room
  function broadcastToRoom(room: string, senderWs: WebSocket, payload: any) {
    const rawMessage = JSON.stringify(payload);
    for (const [ws, conn] of clients.entries()) {
      if (conn.room === room && ws !== senderWs && ws.readyState === WebSocket.OPEN) {
        ws.send(rawMessage);
      }
    }
  }

  // Handle WebSocket HTTP upgrades
  server.on("upgrade", (request, socket, head) => {
    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit("connection", ws, request);
    });
  });

  // Health API route
  app.get("/api/health", (req, res) => {
    res.json({ status: "ok", activeRooms: Array.from(new Set(Array.from(clients.values()).map(c => c.room).filter(Boolean))) });
  });

  // Setup Vite Dev server middleware or static serving
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  // Bind to port 3000 as required
  server.listen(PORT, "0.0.0.0", () => {
    console.log(`[BABY-MONITOR] Server is running on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((error) => {
  console.error("Failed to start baby monitor server:", error);
});
