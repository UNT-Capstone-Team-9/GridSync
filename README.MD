# GridSync

## Team
CLOUD9

## Course
CSCE 4901 Capstone I

## Project Overview
GridSync is a sports communication application designed to improve how coaches deliver both offensive and defensive plays during practices and simulated game situations. Instead of relying on verbal communication or printed play sheets, coaches will be able to select a play on a tablet and instantly send individualized assignments to players through Android wearable devices. The goal of the project is to build a working prototype that demonstrates structured one way communication over a controlled local network.

## Team Members
Haley Kang  
Raj Shakya  
Kunsang Gurung  
Esemfon Edet  
Solomon Legesse  

## Instructor
Professor Diana Rabah

## TAs
Jordan Black  
Sai Sri Harsha Chakravarthula  


## Update Notes (v3)
- **Swipe to go back:** swipe left-to-right on any inner screen (the back button still works). On the Create Play canvas the swipe must start at the very left edge so drawing never triggers it.
- **Custom watch names:** Assign Watches > tap a watch > *Rename watch* (e.g. "QB Watch"). The name is used across the app and is remembered per watch.
- **Back button:** new minimal "<" chevron on every screen.
- **Session Activity Log:** grouped by date (Today / Yesterday / full date) into Session 1, 2, ...; tap a session to expand, trash icon deletes one session, "Clear all" deletes everything. Logs are now saved between app launches.
- **Hurry-Up Package:** dashboard tile opens the Hurry-Up screen. Pick plays from the Play Library with the list icon, tap a play to send it instantly to all assigned watches (each watch gets only its own role assignment), long-press a play to remove it.
- **Settings:** new gear icon and a card-based Settings page (Play Backup, Session Activity Log).
- **Sending reliability:** plays to each watch are now queued in order and written one at a time, so rapid Hurry-Up taps can't interleave or reorder.
