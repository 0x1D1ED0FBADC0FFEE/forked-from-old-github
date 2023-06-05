import socket
import sys
import threading
from frame import *
import time
from framedefs import *
from ccsdefs import *

#Central Star CCS node
class switchCS:
    def __init__(self):
        self.framebuffer = []
        self.keeprunning = True #when this is set to false, the remaining frames will be processed, then it terminates
        self.st = {} #switching table will be a dictionary
        self.connections = [] #global variable to keep track of all sockets
        self.fwconfig = {}#firewall config, will be dictionary
        
    #main behavior
    def start(self):
        self.readConfig()
        self.openConnections()
        
        
    #read firewall config and store in dedicated variable fwconfig
    def readConfig(self):
        config = open("CCSconfig.txt","r")
        conflines = config.read().split('\n')

        for line in conflines:
            currentline = line.split(":")
            self.fwconfig[currentline[0]] = currentline[1].strip()
    
    #open connections, be ready to make threads to handle multiple CAS 
    def openConnections(self):

        HOST = '127.0.0.1'
        PORT = CCS_PORT 
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)   
        s.bind((HOST, PORT))      

        # a thread that will process frames out of the frame buffer
        framesthread = threading.Thread(target=self.processFrames, args=())
        framesthread.start() 

        #set socket parameters, listen and timeout
        s.listen(500)
        s.settimeout(3)
      
        threads = []#store threads
        while(self.keeprunning):
            try:
                connection, address = s.accept()
                
            except socket.timeout:
                #if no frames are to be sent anymore, will time out and break infinite loop
                self.keeprunning = False
                continue
            self.connections.append(connection)
            t1 = threading.Thread(target=self.handleConnection, args=(connection,))
            t1.start()#start new thread per connection and append it to thread list
            threads.append(t1)

        print("CCS shutting down")

    #Handle connection, this is called as a thread
    def handleConnection(self,connection):
        i = 0
        while(i<6):
            received = connection.recv(25500)
            if(received!=b''):

                if(received[ACK_TYPE_FIELD]==5):#this frame tells the CCS where a CAS is
                    self.newSTentry(received[SRC_FIELD], connection)
                    received = self.setNextFrame(received)
                    continue;
            i+=1
            if(received==b'\x00\x00\x00\x00\x00\x00\x00'): #remote shutdown, if this is received, get ready for shutdown (after processing remaining frames). not used, but works.
                self.keeprunning = False
            
            self.getFrames(received)

    #add entry to switching table
    def newSTentry(self, id, connection):
        self.st[id] = connection

    #offsets received by the last frame, gets rid of recently processed frame
    def setNextFrame(self, received):
        return received[BYTES_OF_FIELDS + received[SIZE_FIELD]:]

    #process all frames currently written into the buffer from the socket
    def processFrames(self):

        while(self.keeprunning):
            try:
                nextframe = self.framebuffer[0]
            except:
                continue
            bytefr = nextframe.getByteFrame()
            destination = bytefr[DST_FIELD] #get destination field from frame
            source = bytefr[SRC_FIELD]
            
            if(not(self.canForward(source,destination))):
                #TODO tell source frame was rejected based on FW
                break

            #FORWARD to proper CAS
            high = destination//16 #higher 4 bits of value, represents the CAS
            
            #find corresponding CAS
            dest = self.destinationLookup(high)
            #send frame and discard copy
            dest.send(bytefr)
            self.framebuffer = self.framebuffer[1:]


    #See if this can be forwarded based on firewall config
    #TODO
    def canForward(self, src, dest):
        return True

    #process the current frames in socket
    def getFrames(self, received):
        while(received != b''):
            frame = self.extractFirstFrame(received)
            self.framebuffer.append(frame)
            received = self.setNextFrame(received)

    #get first frame in received
    def extractFirstFrame(self, received):
                size = received[SIZE_FIELD]
                rawbytes = received[0:size + BYTES_OF_FIELDS]
                return self.BytesToFrame(rawbytes)
                
    #received bytes to frame, then return frame object
    def BytesToFrame(self, received):
        fr = frame( dest = received[DST_FIELD],
                    src = received[SRC_FIELD],
                    crc = received[CRC_FIELD],
                    size = received[SIZE_FIELD],
                    acktype = received[ACK_TYPE_FIELD],
                    data = str(received[DATA_FIELD:])[2:-1])
        return fr

    #look up switching table entry and return -1 if not found
    def destinationLookup(self, id):
        temp = self.st.get(id)
        if temp is not None:
            return temp
        else:
            return -1
    #another way to shutdown, but doesnt seem to work currently
    def shutdown(self):
        self.keeprunning = False
               
