import express from "express";
import { authenticateUser } from "../middleware/auth.js";
import * as lmsController from "../controllers/lmsController.js";

const router = express.Router();

router.get("/me", authenticateUser, lmsController.getLmsMe);
router.get("/health", lmsController.getLmsHealth);

export default router;
