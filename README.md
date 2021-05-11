# Arduino Fencing Scoring Box mark 1

## Introduction

This fencing scoring box project is based on previous software and hardware designs by wnew and digitalwestie. Due credit and thanks are hereby given to both designers.

The software and hardware designs described in this document are by Robin Terry, Skipton, Yorkshire, UK, and are derived from the above designs.

The designs, plus this document, are licensed under GPL version 3. All provisions contained in that license must be complied with regarding the designs and regarding this document.

Arduino libraries IRRemote, IRLib2 and TM1637Display are used in the software for this project. Due credit and thanks are hereby given to the authors of these libraries.

## Brief description

- This is a fencing scoring box based on the Arduino Nano.

- It supports all three weapons (foil, epee and sabre) which are selectable via a push button.

- Unlike the previous projects, there is only the 'all-weapon' firmware. 

- There is no 'single-weapon' version of the firmware.

- The box also supports a 4-digit 7-segment LED display, an IR handset, a buzzer and various indicator LEDs.

- The box has three main operating modes - sparring, bout and stopwatch.

- The power supply connector on the box is a 5V USB type B, which allows the box to be powered from a standard USB charger or a USB battery pack.

- More details, including the full circuit diagram, are in the Word document which is found in the 'docs' directory.
