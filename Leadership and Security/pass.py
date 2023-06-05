'''
Should run in any environment that runs python3 using

python3 pass.py

With an emphasis on all files needing to be present in the directory
from which this runs. It may take several seconds to load, tkinter may 
be computationally expensive.

DOCUMENTATION can be found in the project proposal pdf.

Brief summary: a clear text password should be entered, then the button 'Generate Phrase' should be used to generate a phrase
that represents the password and would be, in theory, easier to remember. The phrase can be decoded by putting it in the
field and then hitting 'Get password'

'''

import tkinter as tk
import json
import random

class dicts():
    dict_nouns = []
    dict_verbs = []
    dict_adjs = []
    dict_nums = []



def store_dicts(dicts):
    dicts_dict = {
        'dict_nouns': dicts.dict_nouns,
        'dict_verbs': dicts.dict_verbs,
        'dict_adjs': dicts.dict_adjs,
        'dict_nums': dicts.dict_nums
    }
    with open('dicts.json', 'w') as f:
        json.dump(dicts_dict, f)
    status_bar.config(text="Dictionaries saved.")

def read_dicts(dicts):
    with open('dicts.json', 'r') as f:
        dicts_dict = json.load(f)
    dicts.dict_nouns = dicts_dict['dict_nouns']
    dicts.dict_verbs = dicts_dict['dict_verbs']
    dicts.dict_adjs = dicts_dict['dict_adjs']
    dicts.dict_nums = dicts_dict['dict_nums']
    status_bar.config(text="Dictionaries loaded.")

def shuffle_dicts(dicts):
    random.shuffle(dicts.dict_nouns)
    random.shuffle(dicts.dict_verbs) 
    random.shuffle(dicts.dict_adjs)
    random.shuffle(dicts.dict_nums) 

def generate_phrase(dicts):
    if not password_entry.get():
        status_bar.config(text="Error: no password given.")
        return
    
    #turn password into binary string
    binary_string = ''.join([format(ord(c), '08b') for c in password_entry.get()])
    origlen = len(binary_string)#store original length before padding
    #right-pad it with 0 until its a multiple of 10 in length
    while(len(binary_string)%10 != 0):
        binary_string =  binary_string + '0'

    #get length difference between original binary representation and padded version
    len_diff = len(binary_string) - origlen

    #get binary of length difference and left-pad it with zeros so that it becomes 4 bits
    len_diff_bin = bin(len_diff)[2:]
    while(len(len_diff_bin) < 4):
        len_diff_bin = '0' + len_diff_bin

    #append binary representation of the length difference to the end, needed for decoding step
    binary_string = binary_string + len_diff_bin
    phrase = ""
    print("binstring " + binary_string)
    i = 0

    while( (len(binary_string) - i) != 4 ):
        
        if(i % 30 == 20):
            addphrase = (dicts.dict_verbs[int(binary_string[i:i+10], 2)])
        if(i % 30 == 10):
            addphrase = (dicts.dict_nouns[int(binary_string[i:i+10], 2)])
        if(i % 30 == 0):
            addphrase = (dicts.dict_adjs[int(binary_string[i:i+10], 2)])

        phrase += addphrase
        phrase += " "
        i += 10
    
    phrase += (dicts.dict_nums[int(binary_string[len(binary_string)-4:], 2)])

        
    phrase_entry.delete(0, tk.END)
    phrase_entry.insert(0, phrase)
    status_bar.config(text="Phrase generated.")

def get_word_index(dicts, w):

    if w in dicts.dict_nouns:
        return dicts.dict_nouns.index(w)
    if w in dicts.dict_verbs:
        return dicts.dict_verbs.index(w)
    if w in dicts.dict_adjs:
        return dicts.dict_adjs.index(w)
    if w in dicts.dict_nums:
        return dicts.dict_nums.index(w)
    return -1

def get_password(dicts):
    if not phrase_entry.get():
        status_bar.config(text="Error: no phrase given.")
        return

    words = phrase_entry.get().split(' ')
    padded_word = words[-1]
    padded_bits = dicts.dict_nums.index(padded_word)

    # concatenate all the binary strings for the non-padded words
    binphrase = ''
    for w in words[:-1]:
        temp = bin(get_word_index(dicts, w))[2:]
        while(len(temp) < 10):
            temp = '0' + temp
        binphrase += temp

    # calculate the length of the unpadded binary string
    unpadded_len = len(binphrase) - padded_bits

    # truncate the binary string to the unpadded length
    binphrase = binphrase[:unpadded_len]

    # convert the binary string to ASCII characters
    pw = ""
    for i in range(0, len(binphrase), 8):
        pw += chr(int(binphrase[i:i+8], 2))

    password_entry.delete(0, tk.END)
    password_entry.insert(0, pw)
    status_bar.config(text="Password re-constructed.")

def load_dicts(dicts):
    with open("verbs.txt", 'r') as txtfile:
       dicts.dict_verbs = txtfile.read().strip().split(',')
    with open("adjs.txt", 'r') as txtfile:
       dicts.dict_adjs = txtfile.read().strip().split(',')
    with open("nouns.txt", 'r') as txtfile:
       dicts.dict_nouns = txtfile.read().strip().split(',')
    with open("nums.txt", 'r') as txtfile:
       dicts.dict_nums = txtfile.read().strip().split(',')


       

# create main window
root = tk.Tk()
root.title("Passphrase Generator")

# create labels and input fields
phrase_label = tk.Label(root, text="Phrase:")
phrase_label.grid(row=0, column=0, padx=5, pady=5)
phrase_entry = tk.Entry(root)
phrase_entry.grid(row=0, column=1, padx=5, pady=5, sticky="EW")
root.columnconfigure(1, weight=1)

password_label = tk.Label(root, text="Password:")
password_label.grid(row=1, column=0, padx=5, pady=5)
password_entry = tk.Entry(root)
password_entry.grid(row=1, column=1, padx=5, pady=5, sticky="EW")

# create buttons
generate_button = tk.Button(root, text="Generate Phrase", command=lambda:generate_phrase(dicts))
generate_button.grid(row=2, column=0, padx=5, pady=5)

get_password_button = tk.Button(root, text="Get Password", command=lambda:get_password(dicts))
get_password_button.grid(row=2, column=1, padx=5, pady=5)

store_button = tk.Button(root, text="Store dicts", command=lambda:store_dicts(dicts))
store_button.grid(row=3, column=0, padx=5, pady=5)

load_button = tk.Button(root, text="Load dicts", command=lambda:read_dicts(dicts))
load_button.grid(row=3, column=1, padx=5, pady=5)

load_button = tk.Button(root, text="Shuffle dicts", command=lambda:shuffle_dicts(dicts))
load_button.grid(row=3, column=3, padx=5, pady=5)

# create status bar
status_bar = tk.Label(root, text="", bd=1, relief=tk.SUNKEN, anchor=tk.W)
status_bar.grid(row=4, column=0, columnspan=2, sticky=tk.W+tk.E, padx=5, pady=5)

# resize input fields
root.grid_columnconfigure(1, weight=1)
root.grid_rowconfigure(0, weight=1)
root.grid_rowconfigure(1, weight=1)

#load dictionaries
dict = dicts()
load_dicts(dicts)

print(get_word_index(dict, "alters"))
print(dict.dict_nouns)
print(len(dict.dict_nouns))
# start main loop
print(get_word_index(dicts, 'dkjfieo'))
root.mainloop()
