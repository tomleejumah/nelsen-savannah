import express from 'express';
import { authenticateUser } from '../middleware/auth.js';
import * as chatController from '../controllers/chatController.js';

import { requireRoles } from "../middleware/lmsRoles.js";
import { getCourseChatCandidates, createCourseGroupChat } from "../controllers/courseGroupChatController.js";

const router = express.Router();

router.get("/course/:trackId/candidates", authenticateUser, requireRoles("Mentor"), getCourseChatCandidates);
router.post("/course/:trackId/groups", authenticateUser, requireRoles("Mentor"), createCourseGroupChat);

// Send chat message notification
router.post('/message', authenticateUser, chatController.sendChatMessageNotification);

// Get chat conversations
router.get('/conversations/:userId', authenticateUser, chatController.getConversations);

// Mark chat as read
router.patch('/conversations/:conversationId/read', authenticateUser, chatController.markChatAsRead);

export default router;
