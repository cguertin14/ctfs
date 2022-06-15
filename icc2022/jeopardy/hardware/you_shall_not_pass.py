import serial, sys

port = '/dev/pts/2'
baudrate = 115200

ser = serial.Serial(port,baudrate,timeout=0.001)
while True:
    data = ser.read(1)
    data+= ser.read(ser.inWaiting())
    ser.write(b'help()')
    # sys.stdout.write('help()')
    # sys.stdout.flush()