java_home_8 := "/Users/hobofan/Library/Java/JavaVirtualMachines/corretto-1.8.0_422/Contents/Home"
java_home_20 := "/Users/hobofan/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home"

out_artifact := "JepUvExample-0.1.0-SNAPSHOT.jar"

run_jre8:
    # Command to build and run the project with JDK8/JRE8
    export JAVA_HOME={{java_home_8}}
    mvn clean package -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8
    java -jar target/{{out_artifact}}

run_jre20:
    # Command to build and run the project with JDK20/JRE20
    export JAVA_HOME={{java_home_20}}
    mvn clean package -Dmaven.compiler.source=20 -Dmaven.compiler.target=20
    java -jar target/{{out_artifact}}
