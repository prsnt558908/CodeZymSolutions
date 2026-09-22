@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Create the new question folder beside this batch file.
pushd "%~dp0" >nul

set "folderName=%~1"
if not defined folderName set /p "folderName=Enter the folder name: "

if not defined folderName (
    echo Error: A folder name is required.
    popd
    exit /b 1
)

rem The question number is the part before the first hyphen.
for /f "tokens=1 delims=-" %%I in ("%folderName%") do set "questionId=%%I"

if not defined questionId (
    echo Error: Could not read the question number from "%folderName%".
    popd
    exit /b 1
)

mkdir "%folderName%" 2>nul
if not exist "%folderName%\." (
    echo Error: Could not create the folder "%folderName%".
    popd
    exit /b 1
)

call :createTutorialFile "%folderName%\q-%questionId%-java-tutorial.md"
call :createTutorialFile "%folderName%\q-%questionId%-python-tutorial.md"

echo Done: "%folderName%"
popd
exit /b 0

:createTutorialFile
set "outputFile=%~1"

rem Do not overwrite a tutorial that already contains work.
if exist "%outputFile%" (
    echo Skipped existing file: "%outputFile%"
    exit /b 0
)

>"%outputFile%" echo #### Problem Statement
>>"%outputFile%" echo(
>>"%outputFile%" echo [https://codezym.com/question/%folderName%](https://codezym.com/question/%folderName%)
echo Created: "%outputFile%"
exit /b 0
