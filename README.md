# PreFlight Jenkins Plugin

## Description
This documentation provides you, how to run or trigger your PreFlight tests from Jenkins.   

## Usage
`clientId (required)` : Your Client ID. You can get it from [Account Settings > API](https://app.preflight.com/account/api) under your PreFlight account.
 
`clientSecret (required)` : Your Client Secret. You can get it from [Account Settings > API](https://app.preflight.com/account/api) under your PreFlight account.

`testId (optional)` : Pass the Test Id to run. If test id or group id are not passed all the tests will be run.

`groupId (optional)` : Pass the Group Id to run. If test id or group id are not passed all the tests will be run. You can get it from [Test Settings > Groups](https://app.preflight.com/tests/settings/groups) under your PreFlight account.

`environmentId (optional)` : Environment ID for your test group. You can get it from [Test Settings > Environments](https://app.preflight.com/tests/settings/environments) under your PreFlight account.

`platforms (optional)` : Platforms and browsers you want to run your PreFlight tests.  
  * Example usage `win-chrome`
  * You can pass more than one browser option. Ex. `win-chrome, win-firefox`
  * Platform options : `win`
  * Browser options : `chrome`, `ie`, `edge`, `firefox`

`sizes (optional)` :  Size you want to run your PreFlight tests.
  * Example usage. (WidthxHeight) `1440x900`
  * You can pass more than one size option. Ex. `1920x1080, 1440x900`
  * Size options : `1920x1080, 1440x900, 1024x768, 480x640`

`captureScreenshots (optional)` :  Enables taking screenhots of the each step.

`waitResults (optional)` :  If you set it as `true`, your build waits your PreFlight test results.


## Developer Notes

###### Environment Setup
Before you run this project you need to prepare your development environment. [This](https://jenkins.io/doc/developer/tutorial/prepare/) is a helpful document to prepare it.

In this project used NetBeans as an IDE.  

###### Build and Run
After prepare your environment download or clone the project. 

To run project open a command prompt and navigate to source. Then execute `mvn hpi:run` command. After build open a browser and navigate to `http://localhost:8080/jenkins/`. Local Jenkins instance will be up and running for the development.
