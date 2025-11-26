import express from 'express';
import './config/firebase.js';
import notificationRoutes from './routes/notifications.js';
import chatRoutes from './routes/chat.js';

const app = express();
app.use(express.json());

// Health check
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    service: 'nisisi-africa',
    timestamp: new Date().toISOString() 
  });
});

// Routes
app.use('/notifications', notificationRoutes);
app.use('/chat', chatRoutes);

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Route not found' });
});

// Error handler
app.use((err, req, res, next) => {
  console.error('Error:', err);
  res.status(500).json({ error: 'Internal server error' });
});

const PORT = process.env.PORT || 5002;
app.listen(PORT, () => {
  console.log(`Nisisi Africa service running on port ${PORT}`);
});
