@echo off
echo ========================================
echo   BayBIS Test Interface
echo ========================================
echo.
echo Opening test interface in browser...
echo.
echo NOTE: This opens the HTML file directly.
echo The search will use the Java command-line tool.
echo.

start index.html

echo.
echo To search, use the command-line tool instead:
echo.
echo   cd ..
echo   java -cp "target\classes;..." de.formcycle.baybis.ManualBayBisTrigger spec/test/FachspezifischBehoerdenauskunft005a-1332.xml
echo.

pause
