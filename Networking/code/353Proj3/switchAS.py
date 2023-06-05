import socket
import sys
import threading
from frame import *
import time
from framedefs import *
from ccsdefs import *

#CAS switch
class switchAS:
    def __init__(self, id, port):
        self.framebuffer = []
        self.keeprunning = True #when this is set to false, the remaining frames will be processed, then it terminates
        self.st = {} #switching table will be a dictionary
        self.connections = [] #global to keep track of all sockets
        self.id = id #will be a number
        self.port = port #port on which this will listen
        self.CCSconnection = self.initCCS() #create socket to communicate with CCS, used by all threads
        self.regWithCCS()#Tell CCS your ID and your listening port


    def initCCS(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        print ("CAS created socket for sending to CCS")
        port = CCS_PORT

        # connect to CCS
        i=0
        while(i<=5):#limit the number of connect attempts
            try:
                s.connect(("localhost", port))

                break
            except:
                i+=1   

        if(i==6):
            print("CAS was unable to connect to CCS, shutting down")
            return

        return s

    #register self with CCS, send a special frame with ack type 5 to it
    #this should happen during initalization, unasked, because the
    #port of the CCS is globally known and unique
    def regWithCCS(self): 
                portstr = str(self.port)
                fr = frame(0, self.id, 5, len(portstr), 5, str(portstr)) #Special frame format, acktype 5 means frame is CAS registering with CCS
                self.CCSconnection.send(fr.getByteFrame())#Tell the CCS your ID and which port you are listening on. 
  
   #Handle connection, this is called as a thread
    def handleConnection(self,connection, address):
        
        while(self.keeprunning):
            received = connection.recv(25500)

            if(received != b''):

                if(received[ACK_TYPE_FIELD]==6):#this frame tells the CAS where a node is 
                    self.newSTentry(received[SRC_FIELD], connection)
                    received = self.setNextFrame(received)
                    continue;

                self.getFrames(received)

    #get all frames out of what was found in the socket, and add them to the frame buffer    
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

    #make switching table entry        
    def newSTentry(self, id, connection):
        self.st[id] = connection

    #look up switching table entry and return -1 if not found
    def destinationLookup(self, id):
        temp = self.st.get(id)
        #print("CAS DOESNT KNOW NODE ", id)

        if temp is not None:
            return temp
        else:
            return -1


    def BytesToFrame(self, received):
        fr = frame( dest = received[DST_FIELD],
                    src = received[SRC_FIELD],
                    crc = received[CRC_FIELD],
                    size = received[SIZE_FIELD],
                    acktype = received[ACK_TYPE_FIELD],
                    data = str(received[DATA_FIELD:])[2:-1])
        #print ("CAS BYTES TO FRAME BUILT ", fr.getByteFrame())
        return fr



    def shutdown(self):
        self.keeprunning = False
    
    #Send hardcoded frame with acktype 6 to node, which will prompt it to return id to CAS
    def findNode(self, id):
        fr = frame(id, 0,0,1,6,"A")
        #time.sleep(0.05)
        for c in self.connections:
            c.send(fr.getByteFrame())

     #process all frames currently written into the buffer from the socke
    def processFrames(self):
        while(self.keeprunning):
            try:
                nextframe = self.framebuffer[0]
            except:
                continue
            bytefr = nextframe.getByteFrame()
            #print("byteframe in CAS$$$ " ,bytefr)
            destination = bytefr[DST_FIELD] #get destination field from frame
            high = destination//16 #higher 4 bits of value, represents the CAS
            low = destination % 16
            
            if(high == self.id):#if first digit is id of this CAS, forward frame internally. If not, send to CCS.
                dest = self.destinationLookup(destination)
                if(dest == -1):
                    #print("FINDNOED {}", destination)
                    self.findNode(destination)
                    #time.sleep(0.05)
                    continue
                #print("CAS SENDING TO NODE ", bytefr)

                dest.send(bytefr)
                self.framebuffer = self.framebuffer[1:]
            else:
                #print("CAS sends frame to CSS ", bytefr)
                self.CCSconnection.send(bytefr)
                self.framebuffer = self.framebuffer[1:]

    #method to make the CAS exhibit main behavior
    def start(self): 
        #first thing, start listening to CCS
        CCSthread = threading.Thread(target=self.handleConnection, args=(self.CCSconnection, None,))
        CCSthread.start()

        # specify Host and Port
        HOST = '127.0.0.1'
        PORT = self.port

        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)   
        s.bind((HOST, PORT))       

        framesthread = threading.Thread(target=self.processFrames, args=())
        framesthread.start()

        threads = []
        s.listen(50)
        s.settimeout(3)
        while(self.keeprunning):
            try:
                connection, address = s.accept()
                
            except socket.timeout:
                self.keeprunning = False
                print("CAS shutting down")
                continue
            self.connections.append(connection)

            t1 = threading.Thread(target=self.handleConnection, args=(connection, address,))
            t1.start()
            threads.append(t1)

        for t in threads: 
            t.join()

        framesthread.join()

    
    #offsets received by the last frame, gets rid of recently processed frame
    def setNextFrame(self, received):
        return received[BYTES_OF_FIELDS + received[SIZE_FIELD]:]