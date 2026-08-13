import express from "express";
import { authenticateUser } from "../middleware/auth.js";
import * as notificationController from "../controllers/notificationController.js";

const router = express.Router();

// Send like notification
router.post(
  "/like",
  authenticateUser,
  notificationController.sendLikeNotification,
);

// Send comment notification
router.post(
  "/comment",
  authenticateUser,
  notificationController.sendCommentNotification,
);

// Send new-event notification
router.post(
  "/event",
  authenticateUser,
  notificationController.sendEventNotification,
);

// Get user notifications
router.get(
  "/:userId",
  authenticateUser,
  notificationController.getUserNotifications,
);

// Mark notification as read
router.patch(
  "/:userId/:notificationId/read",
  authenticateUser,
  notificationController.markAsRead,
);

// Delete notification
router.delete(
  "/:userId/:notificationId",
  authenticateUser,
  notificationController.deleteNotification,
);

export default router;
