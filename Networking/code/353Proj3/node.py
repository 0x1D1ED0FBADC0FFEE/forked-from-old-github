import socket
import sys
from frame import *
import time
from framedefs import *

class node:
    def __init__(self, id, cas_port):
        self.id = id
        self.cas_port = cas_port #will be told the port number of their dedicated CAS
        self.cas_connection = None

    #main behavior of node
    #connect to CAS, be ready to send and then receive frames
    def start(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        print ("NODE Socket successfully created")
        port = self.cas_port
        
        # connect to CAS
        i=0
        while(i<=5):#limit the number of connect attempts
            try:
                s.connect(("localhost", port))
                break
            except:
                i+=1

        self.cas_connection = s
        if(i==6):
            print("node was unable to connect, shutting down")
            return

        #open config file to send your lines
        config = open("node" + str(self.id) + ".txt")
        conflines = config.read().split('\n')
        if(conflines[-1]==''):
            conflines = conflines[:-1]

        #PROCESS all lines in your config
        currentFrame = None
        for line in conflines:
            currentFrame = self.lineToFrame(line) #frame(self.id,int(line[0]),line[1])
            s.send(currentFrame.getByteFrame())
        s.send(b'')
    
        file = open("node"+str(self.id)+"output.txt","w+")
        s.settimeout(3)
        i=0
        while(i<=2):
            try:
                #a node will only wait a limited number of times
                #to receive data, for one second each time.
                #under normal execution, it should receive shutdown
                #frames anyways, so this is a last line of defense
                received = s.recv(25500)
            except:
                
                continue

            if(received == 0):#telling the node to shut down
                break
            
            if(received == b''):
                continue

            if(received[ACK_TYPE_FIELD]==6 ): #if ack has value 6, CAS wants to know where node is
               #only register with CAS if this was meant for this node
                if(received[DST_FIELD]==self.idToNum()):
                    self.regWithCAS()
                received = self.setNextFrame(received)
                continue

            #will print the current state of the socket.
            #print("NODE RECEIVED ", received)
       
            size = received[SIZE_FIELD]
            firstframe = received[0:size + BYTES_OF_FIELDS]
            
            srcb = firstframe[SRC_FIELD]
            src = str(srcb//16) + "_" + str(srcb%16)
            

            print("WILL WRITE TO OUTPUT:" "From "+ str(src) + ":" + str(firstframe[DATA_FIELD:size+DATA_FIELD].decode())+"\n")
            file.write("From "+ str(src) + ":" + str(firstframe[DATA_FIELD:size+DATA_FIELD].decode())+"\n")
            received = received[size+DATA_FIELD:]
            i = 0#reset wait attempts

        file.close()

    #set offset to next frame, which will then be read as bytes again
    def setNextFrame(self, received):
        return received[BYTES_OF_FIELDS + received[SIZE_FIELD]:]

    #will parse a line and return the frame for it
    def lineToFrame(self, line):
        line = line.split(':') 
        line[0] = line[0].split("_")
     
        
        dest = int(line[0][0])*16+int(line[0][1])
        src = self.idToNum()
        crc = 0#TODO
        size = len(line[1])
        acktype =0
        data = line[1]

        fr = frame(dest=dest,
                    src=src,
                    crc=crc,
                    size=size,
                    acktype=acktype,
                    data=data) 
        return fr
    
    #helper function, will give string represantion of node name
    def idToNum(self):
        id = self.id
        id = id.split("_")
        return int(id[0])*16 + int(id[1])

    #make node tell CAS where it is
    def regWithCAS(self): 
            fr = frame(0, self.idToNum(), 0, 1, 6, "A") #Special frame, acktype 6 means frame is node registering with CAS
            self.cas_connection.send(fr.getByteFrame())#Tell the CAS your ID and which port you are listening on. 