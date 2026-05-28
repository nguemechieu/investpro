@echo off
cd /d "c:\Users\nguem\Documents\GitHub\investpro"
git add src/main/java/org/investpro/ui/TradingDesk.java

REM Create a temporary file for the commit message since multi-line messages need special handling
(
echo fix: make refreshTradabilitySnapshot async to avoid JavaFX thread timeout
echo.
echo Blocking .get^(8, TimeUnit.SECONDS^) on 68 OANDA symbols caused TimeoutException
echo on the JavaFX Application Thread. Now runs getTradability^(^) as a CompletableFuture
echo chain and applies results back via Platform.runLater^(^), keeping the UI responsive.
echo.
echo Co-authored-by: Copilot ^<223556219+Copilot@users.noreply.github.com^>
) > commit_msg.txt

git commit -F commit_msg.txt
git push origin main
del commit_msg.txt
