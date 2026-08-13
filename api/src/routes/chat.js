import express from 'express';
import { authenticateUser } from '../middleware/auth.js';
import * as chatController from '../controllers/chatController.js';

const router = express.Router();

// Send chat message notification
router.post('/message', authenticateUser, chatController.sendChatMessageNotification);

// Get chat conversations
router.get('/conversations/:userId', authenticateUser, chatController.getConversations);

// Mark chat as read
router.patch('/conversations/:conversationId/read', authenticateUser, chatController.markChatAsRead);

export default router;
