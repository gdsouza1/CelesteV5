cd D:\Programming Projects\Idea\Celestev5\

IF %1==deploy (
gradle clean
gradle shadowJar
cd build\libs
IF not exist Procfile (
echo worker: java $JAVA_OPTS -jar ./Celestev5.jar>Procfile
)
heroku ps:scale -a celestev5 worker=0
heroku deploy:jar -a celestev5 --jdk 13 --jar Celestev5.jar -i Procfile
heroku ps:scale -a celestev5 worker=1
goto commexit
)

IF %1==start heroku ps:scale -a celestev5 worker=1 && goto commexit
IF %1==stop heroku ps:scale -a celestev5 worker=0 && goto commexit
IF %1==tlogs heroku logs -a celestev5 --tail && goto commexit
IF %1==logs heroku logs -a celestev5 && goto commexit

:commexit
cd .. && cd ..
pause

