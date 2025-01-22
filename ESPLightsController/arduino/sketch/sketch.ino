#include <BluetoothSerial.h>
#include <SafeString.h>
#include <SafeStringReader.h>

String device_name = "ESP32-Lights-Controller";

BluetoothSerial myespSerial;
#define RED_LED 19 
#define GREEN_LED 18
#define BLUE_LED 21
#define BLUETOOTH_MILLIS 25 // millis for bluetooth check

createSafeStringReader(command, 50, "\r\n"); //safe string reader for full command value
createSafeString(field, 20);
int msDelay = 2000; //milliseconds of delay
float speedSlider=0;
char current_mode='C'; // S for Static, B for Blinking, F for Fading, M for Moving. C means continuation of task.
float redSlider=0;
float greenSlider=0;
float blueSlider=0;
TaskHandle_t modeSwitch; // handle for current second core task

int float_to_analog(float num) {
  if (num <= 0.02) return 0;
  else if (num >= 0.98) return 255;
  else return (int)(num*255);
}

int speedConvert(float num) {
  return (int)(-1800*num+2000);
}

void setup() {
  myespSerial.begin(device_name);  //Bluetooth device name
  command.connect(myespSerial);  // connect reader to bluetoothserial
  //myespSerial.deleteAllBondedDevices(); // Uncomment this to delete paired devices; Must be called after begin
  pinMode(RED_LED, OUTPUT);
  pinMode(GREEN_LED, OUTPUT);
  pinMode(BLUE_LED, OUTPUT);
  analogWrite(RED_LED, 255);
  analogWrite(GREEN_LED, 255);
  analogWrite(BLUE_LED, 255);
  xTaskCreatePinnedToCore(modeSwitchCode, "modeSwitch", 10000, NULL, 0, &modeSwitch, 0);
}

void set() {
  analogWrite(RED_LED, float_to_analog(redSlider));
  analogWrite(GREEN_LED, float_to_analog(greenSlider));
  analogWrite(BLUE_LED, float_to_analog(blueSlider));
}
void blink() {
  analogWrite(RED_LED, 255);
  analogWrite(GREEN_LED, 255);
  analogWrite(BLUE_LED, 255);
  if (current_mode == 'B') delay(msDelay/2);
  else return;
  if (current_mode == 'B') {
    analogWrite(RED_LED, 0);
    analogWrite(GREEN_LED, 0);
    analogWrite(BLUE_LED, 0);
  } else return;
  delay(msDelay/2);
}
void move() { //redo
  analogWrite(BLUE_LED, 255);
  if (current_mode == 'M') delay(msDelay / 5);
  else return;
  if (current_mode == 'M') analogWrite(RED_LED, 255);
  else return;
  if (current_mode == 'M') delay(msDelay / 5);
  else return;
  if (current_mode == 'M') analogWrite(GREEN_LED, 255);
  else return;
  if (current_mode == 'M') delay(msDelay / 5);
  else return;
  if (current_mode == 'M') analogWrite(BLUE_LED, 0);
  else return;
  if (current_mode == 'M') delay(msDelay / 5);
  else return;
  if (current_mode == 'M') analogWrite(RED_LED, 0);
  else return;
  if (current_mode == 'M') delay(msDelay / 5);
  else return;
  if (current_mode == 'M') analogWrite(GREEN_LED, 0);
  else return;
} 

void fade() {
  for (int i = 0; i < 256; i += 2) {
    if (current_mode == 'F') {
      analogWrite(RED_LED, i);
      analogWrite(GREEN_LED, i);
      analogWrite(BLUE_LED, i);
    } else return;
    delay(msDelay/64);
  }
  for (int i = 255; i >= 0; i-= 2) {
    if (current_mode == 'F') {
      analogWrite(RED_LED, i);
      analogWrite(GREEN_LED, i);
      analogWrite(BLUE_LED, i);
    } else return;
    delay(msDelay/64);
  }
}

void modeSwitchCode(void * parameter) {
  for (;;) {
    if (current_mode == 'S') {
      current_mode = 'C';
      set();
    }
    else if (current_mode == 'B') {
      blink();
    }
    else if (current_mode == 'F') {
      fade();
    }
    else if (current_mode == 'M') {
      move();
    }
  }
}

void loop() {  // loop is ran on core 1
  if (command.read()) { // if command present
    command.firstToken(field, " "); // gets first token
    if (field == "S") { //checks which command it is
      current_mode = 'S';
      command.nextToken(field, " "); // get red
      field.toFloat(redSlider);
      command.nextToken(field, " "); // get green
      field.toFloat(greenSlider);
      command.nextToken(field, " "); // get blue
      field.toFloat(blueSlider);
    }
    else if (field == "B") {
      current_mode = 'B';
      command.nextToken(field, " "); // get speed
      field.toFloat(speedSlider);
      msDelay = speedConvert(speedSlider);
    }
    if (field == "F") {
      current_mode = 'F';
      command.nextToken(field, " "); // get speed
      field.toFloat(speedSlider);
      msDelay = speedConvert(speedSlider);
    }
    if (field == "M") {
      current_mode = 'M';
      command.nextToken(field, " "); // get speed
      field.toFloat(speedSlider);
      msDelay = speedConvert(speedSlider);
    }
  }
}

