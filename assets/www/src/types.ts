export type DeviceRole = "bebek" | "ebeveyn";

export type ConnectionStatus = "disconnected" | "connecting" | "connected";

export interface LogEntry {
  id: string;
  time: string;
  type: "info" | "warning" | "alert" | "error";
  message: string;
}

export interface BabyState {
  volume: number; // Current sound level (0 to 100)
  battery?: number; // Device battery percentage (optional)
  isCrying: boolean; // Is currently crying
  lastCryTime?: string; // Timestamp of last detected cry
  online: boolean; // Is the baby unit currently active/online
}
