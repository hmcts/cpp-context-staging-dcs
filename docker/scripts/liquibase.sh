echo "Running Liquibase"
dbServerName=$1
dbUserName=$2
dbPassword=$3
contextName=stagingdcs
dbPort=${5:-5432}
java -jar stagingdcs-liquibase.jar --url=jdbc:postgresql://${dbServerName}:${dbPort}/${contextName}?sslmode=require --username=${dbUserName} --password=${dbPassword} --logLevel=info update
if [ $? -ne 0 ]
then
    exit 1
else
    echo success!
fi
java -jar jobstore-liquibase.jar --url=jdbc:postgresql://${dbServerName}:${dbPort}/${contextName}jobstore?sslmode=require --username=${dbUserName} --password=${dbPassword} --logLevel=info update
if [ $? -ne 0 ]
then
    exit 1
else
    echo success!
fi
java -jar framework-system-liquibase.jar --url=jdbc:postgresql://${dbServerName}:${dbPort}/${contextName}system?sslmode=require --username=${dbUserName} --password=${dbPassword} --logLevel=info update
if [ $? -ne 0 ]
then
    exit 1
else
    echo success!
fi