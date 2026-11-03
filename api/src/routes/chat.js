import express from 'express';
import { authenticateUser } from '../middleware/auth.js';
import * as chatController from '../controllers/chatController.js';

import { listMentorChatCourses, createCourseGroupChat } from "../controllers/courseGroupChatController.js";

import { listMySchoolAnnouncements, openSchoolAnnouncements } from "../controllers/schoolAnnouncementController.js";

const router = express.Router();

router.get("/courses/mine", authenticateUser, listMentorChatCourses);
router.post("/course/:trackId/groups", authenticateUser, createCourseGroupChat);

router.get("/schools/announcements", authenticateUser, listMySchoolAnnouncements);
router.post("/school/:schoolId/announcements", authenticateUser, openSchoolAnnouncements);

// Send chat message notification
router.post('/message', authenticateUser, chatController.sendChatMessageNotification);

// Get chat conversations
router.get('/conversations/:userId', authenticateUser, chatController.getConversations);

// Mark chat as read
router.patch('/conversations/:conversationId/read', authenticateUser, chatController.markChatAsRead);

export default router;
