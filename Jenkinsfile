node("linux && jdk8") {
    stage "Checkout"
    checkout scm
  
    stage "Build"
    sh "./gradlew clean build"
}
