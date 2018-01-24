cd %~dp0/src
javac Client_GUI.java
cd..
move %~dp0\src\*.class %~dp0\bin

pause