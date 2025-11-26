# Quick Start Guide

## Easiest Way to Start

1. **Double-click** `start-server.bat`
2. **Open browser** to `http://localhost:8000/index.html`
3. **Done!** 🎉

## Test It

Try searching for:
- **Vorname:** Barbara Carina
- **Nachname:** Fischer  
- **Geburtsdatum:** 1992-02-02
- Click **Suchen**

You should see 2 results!

## How It Works

```
┌─────────────┐
│   Browser   │  You interact here
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   server.py │  Python server (port 8000)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Java Tool   │  ManualBayBisTrigger
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   BayBIS    │  AKDB Test Server
└─────────────┘
```

## Troubleshooting

**"Python is not recognized"**
- Install Python 3: https://www.python.org/downloads/
- Or use: `python3 server.py` instead

**"Connection refused"**
- Make sure you ran `start-server.bat`
- Check if port 8000 is free

**"No results found"**
- The person might not exist in BayBIS test database
- Try the test data from above first

## Features

✅ Search by name and birthdate  
✅ Optional address search  
✅ Beautiful UI  
✅ Live JSON display  
✅ No installation needed (just Python)

Enjoy testing! 🚀
