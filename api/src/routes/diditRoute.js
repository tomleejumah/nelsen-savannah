import express from "express";
import diditController from "../controllers/diditController.js";

const router = express.Router();

router.post("/create-session", diditController.createSession);
router.post("/webhook", diditController.handleWebhook);

export default router;
