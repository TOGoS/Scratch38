@echo off

call C:\Users\stevedxfph\bin\init-java-1.8-env.bat
call C:\Users\stevedxfph\stuff\proj\TOGoSUtils\bin\figure-mvn-cmd.bat
%MVN_CMD% %*
