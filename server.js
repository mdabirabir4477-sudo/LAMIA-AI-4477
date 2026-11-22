import express from "express";
const app = express();
app.use(express.json({ limit: "20kb" }));
const PORT = process.env.PORT || 8080;
const AI_API_KEY = process.env.AI_API_KEY;
const AI_API_URL = process.env.AI_API_URL; // provider's chat-completions endpoint
const AI_MODEL = process.env.AI_MODEL || "your-model";
app.post("/api/chat", async (req, res) => {
  const message = String(req.body?.message || "").trim();
  if (!message || message.length > 8000) return res.status(400).json({ error: "Invalid message" });
  if (!AI_API_KEY || !AI_API_URL) return res.status(503).json({ error: "Backend AI credentials/config not set" });
  try {
    const upstream = await fetch(AI_API_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", "Authorization": `Bearer ${AI_API_KEY}` },
      body: JSON.stringify({
        model: AI_MODEL,
        messages: [
          { role: "system", content: "You are LAMIA AI, nickname LAMU. Reply naturally in the user's language, including Bengali or English. Help with everyday tasks, translation, writing, and calculations. Trading questions are educational only: explain candlesticks, support/resistance, wicks, trends, and technical analysis with demo examples. Never promise profits or guaranteed signals. Do not claim to execute trades." },
          { role: "user", content: message }
        ],
        temperature: 0.7
      })
    });
    if (!upstream.ok) return res.status(502).json({ error: "AI provider returned an error" });
    const data = await upstream.json();
    const reply = data.choices?.[0]?.message?.content || "Sorry, I couldn't generate a reply.";
    res.json({ reply });
  } catch { res.status(502).json({ error: "AI service connection failed" }); }
});
app.get("/health", (_, res) => res.json({ ok: true }));
app.listen(PORT, () => console.log(`LAMIA backend listening on ${PORT}`));
