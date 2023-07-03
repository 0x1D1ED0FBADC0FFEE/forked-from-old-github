/*LISP INTERPRETER PROJECT FILE
University: New Mexico Institute of Mining and Technology
Prof. Hamdy Soliman
CSE 324, Spring 2022
Student: A. S.

Written to compile smoothly in environments that have java 11.0.15
using the commands below, after which PROMPT> should display:

javac LispInterpreter.java 
java LispInterpreter

*/




import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.Buffer;
import java.util.*;
import java.io.FileWriter;   // Import the FileWriter class
import java.io.IOException;
import java.io.File; 

public class LispInterpreter {

    public static Hashtable<String,String> vars = new Hashtable<>();/**Used to store all user defined variables */
    public static Hashtable<String, String> litLists = new Hashtable<>(); /**Stores literal lists as strings*/
    public static Hashtable<String, LinkedList> defunList = new Hashtable<>(); /**stores defuns by name and code */
    public static int listcount = 0; /**Used for key generation in hashtable aLists. Will increment for every list */
    public static String validVarName = "[a-zA-Z0-9[Ä][.][-]['][_]]+"; //restricted set of characters that are allowed for variable names
    public static String functionNameAlphabet = "[a-zA-Z0-9[+][-][*][/]['][.][,][_][<][!][=][>]]+"; //less restricted set of characters for function names
    public static String innermostRegex = "[(]{1}?[a-zA-Z0-9[+][-][*][/]['][Ä][.][,][ ][_][<][!][=][>]]+?[)]{1}?"; //this is used to find the innermost, pre-processed functions
    public static boolean errorOccurred = false;
    public static String errorString = "";
   

    public static void main(String[] args) {
        try
        {

            File sessionResults = new File("results.txt");
            if (sessionResults.createNewFile()) 
            {
                System.out.println("Creating file for session: " + sessionResults.getName());
            } 

        
       
            FileWriter sessionWriter = new FileWriter(sessionResults.getName());
            sessionWriter.write("\n\nSESSION LOG :::::::::::::::::\n\n");
            

        String command;
        Scanner input = new Scanner(System.in);

        while(true)
        {
            System.out.print("PROMPT> ");
        
            command = input.nextLine();  // Read user input
            sessionWriter.write("input logged: " + command+"\n");

            if(command.contains("(exit)")||command.contains("(quit)"))
                break;

            if(!matchParenthesis(command))
            {
                System.out.println("Unmatched number of parenthesis in command. Please check syntax.");
                continue;
            }

            while (command.contains("()"))
            {
                command = command.replace("()", "NIL");//process empty list as nil
            }
            String result = processCommand(command);

            if(errorOccurred)
            {
                System.out.println(errorString);
                sessionWriter.write("ERROR logged: " + errorString + "\n");
                errorOccurred = false;
                continue;
            }
            else
            {
                System.out.println("RESULT: " + result);
                sessionWriter.write("Result logged: " + result+"\n");
            }
            
        }
        
        input.close();
        sessionWriter.close();
        System.out.println("Exiting. Have a nice day!");
    }

        catch(IOException e)
        {
            System.out.println("ERROR creating file for session. Do you have permission?");
        }
    }

    /**This checks the input command for whether there is a matching number of opening and closing parenthesis */
    public static boolean matchParenthesis(String command)
    {
        int i = 0;
        int opencount = 0;//counts openin parenthesis
        int closecount = 0; //counts closing ones
        while(i < command.length())
        {   
            if(command.substring(i, i +1).equals("(")) opencount++;
            if(command.substring(i, i +1).equals(")")) closecount++;
            i++;
        }
        return (opencount == closecount);
    }

    public static String processCommand(String command)
    {   
        if(isNumber(command))
        {
            return command;
        }
        if(!command.contains("(")) //if it doesn't contain opening parenthesis, it must be variable name
        {
            return substituteLists(getVarValue(command)).replaceAll("'", "");
        }

        if(command.contains("defun "))
        {
            LinkedList<String> defunargs = parseDefun(command);
            defun(defunargs);
            return defunargs.get(0); //return name of the function that was just added to defun list
        }

        command = processInnermost(command);
        command = substituteLists(command);
        command = command.replaceAll("'", "");
        return command;
    }

    public static LinkedList<String> parseDefun(String command)
    {   

        try{

            int start = 0;
            LinkedList<String> defunargs = new LinkedList<>();
            Pattern pattern = Pattern.compile("[(]{1}[ ]*defun");
            Matcher matcher = pattern.matcher(command);
            matcher.find();//Step over the function name

            matcher.usePattern(Pattern.compile(functionNameAlphabet));
            matcher.find();
            defunargs.add(matcher.group());

            matcher.usePattern(Pattern.compile("[(]{1}" + "[a-zA-Z0-9[Ä][.][ ][-]['][_]]+" + "[)]{1}"));
            matcher.find();
            defunargs.add(matcher.group());

            matcher.usePattern(Pattern.compile("[(]{1}"));
            matcher.find();
            start = matcher.start();

            int opencount = 1;
            int closedcount = 0;
            int i = start;

            while(i < command.length())
            {
                i++;
                
                if(command.substring(i, i + 1).equals("(")) opencount++;
                if(command.substring(i, i + 1).equals(")")) closedcount++;
                if(opencount == closedcount) break; 
            }

            defunargs.add(command.substring(start, i + 1));

            //System.out.println("DEBUFG PARSDEFUN "+ defunargs);
            return defunargs;
        }
        catch(Exception e)
        {
            errorAndAbort("ERROR parsing defun");
            return new LinkedList<String>();
        }

    }

    public static String processInnermost(String command) /* This will process all innermost function calls and substitute them with their results in the string*/
    {   
        //System.out.println("COMM "+ command);
        command = parseLists(command);
        Pattern pattern = Pattern.compile(innermostRegex);
		Matcher matcher = pattern.matcher(command);
        String innerFunction="";
        String result = "";
        StringBuffer buff = new StringBuffer();
        int start = 0;
        int end = 0;

        while(matcher.find())
        {
            
            innerFunction = matcher.group();
            start = matcher.start();
            end = matcher.end();

            
            
            innerFunction = substituteVariables(innerFunction, 0, innerFunction.length());
            
            //System.out.println("found innermost " + innerFunction);
            result = executeFunction(innerFunction);

            
            buff.append(command.substring(0, start) + " " + result + " " + command.substring(end, command.length()));
            command = buff.toString();
            command = parseLists(command);
            matcher.reset(command);
            
            buff.delete(0, buff.length());
            //System.out.println("Done parsing process innermost "+ command);

        }
        
        return command;
    }

    /**parseLists() finds all literal lists, for example '(8 (9 10) 7), which are not functions. 
     * It saves them as a linked list and substitutes them with an expression of the form Ä### in the command string.
     * The umlaut Ä symbol was chosen because it is not otherwise legal in Lisp and can thus be used
     * as a marker for this data type, used in the background only for logistic purposes. Ä### is also used as key in the 
     * dictionary that will store all such lists.  
    */
    public static String parseLists(String command)
    {
        Pattern pattern = Pattern.compile("'[(]");
        Matcher matcher = pattern.matcher(command);
        int start;
        int index;
        int opencount;
        int closedcount;
       
        while(matcher.find())
        {
            
            
            start = matcher.start();
            index = start;

            opencount = 0;
            closedcount = 0;
            while(index < command.length())
            //while(true)
            {
                
                index++;
                
                if(command.substring(index, index + 1).equals("(")) opencount++;
                if(command.substring(index, index + 1).equals(")")) closedcount++;
                if(opencount == closedcount) break;
            }

            
            litLists.put("Ä" + listcount, command.substring(start, index+1));
            StringBuffer buff = new StringBuffer();
            buff.append(command.substring(0, start) + " Ä" + listcount + " " + command.substring(index+1, command.length()));
            command = buff.toString();
            
            matcher.reset(command);

            //System.out.println("SAVED litlist" + litLists.get("Ä" + listcount));
            listcount++;
        }
            return command;
    }

    /** This will substitute the literal lists back into the command string after everything else is processed*/
    public static String substituteLists(String command)
    {   
        Pattern pattern = Pattern.compile("Ä[0-9]*");
		Matcher matcher = pattern.matcher(command);
        //StringBuffer buff = new StringBuffer();
        int start = 0;
        int end = 0;

        while (matcher.find())
        {
            start = matcher.start();
            end = matcher.end();
            command = command.substring(0, start) + litLists.get(matcher.group()).toString() + command.substring(end , command.length());
            matcher.reset(command);
        }

        return command;
    }



    /**Will substitute all variables with their value for one function between start and end positions 
    */
    public static String substituteVariables(String command, int start, int end)
    {   
        //System.out.println(command);
        //System.out.println("[(]{1}[ ]*" + functionNameAlphabet);

        
		Pattern pattern = Pattern.compile("[(]{1}[ ]*"+functionNameAlphabet);
		Matcher matcher = pattern.matcher(command);
        matcher.find();//Step over the function name
        String function = matcher.group();
        boolean skipFirstArg = (function.contains("define") || function.contains("set!")); //if the function is a define, we DO NOT want to substitute the first argument with the variable value
        String gr = "";

        //System.out.println("function start " + function);

        matcher.usePattern(Pattern.compile(validVarName));//from now on, use the pattern that is used for variable names

        StringBuffer buff = new StringBuffer();
        buff.append(function + " ");

        
        while (matcher.find()) {
			//System.out.print("Pattern found from " + matcher.start()+ " to " + (matcher.end()-1)+"::");
			//System.out.print(matcher.group());
            //System.out.println("first " + matcher.group().substring(0,1));

            
            gr = matcher.group();

            //under certain circumstances, no value needs to be found for the variable name, such as when it is NIL, T, a number and so on
            if((gr.substring(0,1).equals("'")) ||(gr.substring(0,1).equals("Ä"))|| isNumber(gr)||skipFirstArg||gr.equals("NIL")||gr.equals("T")) 
            {   
                buff.append(gr + " ");
                skipFirstArg = false;
                continue;
            }


            //System.out.println("Value for " + gr + getVarValue(gr));
            buff.append(getVarValue(gr) + " ");
            
			//System.out.println();
		}
        buff.deleteCharAt(buff.length()-1);
        buff.append(")");

        //System.out.println("DONE PARSING " + buff.toString());

        return buff.toString();
    }
    /**This returns the value of a variable, as string, to be written into the command string*/
    public static String getVarValue(String variableName)
    {
        if(variableName.equals("NIL")||variableName.equals("T"))
            return variableName;
        
        Object variableValue = vars.get(variableName);

        if(variableValue == null)
        {
            //System.out.println("Variable " + variableName + " not found.");
            errorAndAbort("Variable " + variableName + " not found.");
            return "erroringetvarvalue";
        }

        return variableValue.toString();
    }

    public static String executeFunction(String command)
    {   
        /**This function could be a one-liner, but it is step by step to increase readability */
        String function = parseFunctionName(command);
        LinkedList<String> argsList = parseArgsList(command); 
        return selectFunction(function, argsList);
    }

    public static String parseFunctionName(String command)
    {
        
        try
        {
            Pattern pattern = Pattern.compile("[(]{1}[ ]*"+functionNameAlphabet);
            Matcher matcher = pattern.matcher(command);
            
            matcher.find();
            String function = matcher.group();
    
            matcher.reset(function);
            matcher.usePattern(Pattern.compile(functionNameAlphabet));
            matcher.find();
            function = matcher.group();
    
            //System.out.println("FUNCTION found: " + function);
            return function;
        }
        catch(IllegalStateException e)
        {
            return command;
        }

    }

    public static LinkedList<String> parseArgsList(String command)
    {
        LinkedList<String> l = new LinkedList<>();

        Pattern pattern = Pattern.compile("[(]{1}[ ]*"+functionNameAlphabet);
		Matcher matcher = pattern.matcher(command);
        matcher.find();//Step over the function 
        matcher.usePattern(Pattern.compile(validVarName)); //The alphabet for variable names is enough at this point
        while(matcher.find())
        {
            l.add(matcher.group());

        }

       // System.out.println(l.toString());

        return l;
    }


    public static String processDefun(String functionname, LinkedList argsList)

    {   
        LinkedList functionBodyAndParams = (LinkedList) defunList.get(functionname);
        String parameters = functionBodyAndParams.get(0).toString();
        String fbody = functionBodyAndParams.get(1).toString();

        //Start substituting parameters with their values in fbody

        Pattern bodyPattern = null; //these will be used in the below while loop, but are declared here so they don't have to be re-declared every time
		Matcher bodyMatcher = null; 

        Pattern paramPattern = Pattern.compile(validVarName);
		Matcher paramMatcher = paramPattern.matcher(parameters); //extract variables from parameter list
        boolean parenthesis = false; //will indicate whether the replaced group contained a parenthesis

        String currentParam = ""; //will store the parameter we're working on right now
        String bodyGroup = ""; //will hold the group to be replaced (in the body of the function)
        int paramCounter = 0; //keep track of which parameter we are working with

        while(paramMatcher.find())
        {   
            currentParam = paramMatcher.group();
            bodyPattern = Pattern.compile("[ ]+"+ currentParam + "[[ ][)]]{1}");
            bodyMatcher = bodyPattern.matcher(fbody);

            while(bodyMatcher.find())
            {   

                bodyGroup = bodyMatcher.group();
                parenthesis = bodyGroup.contains(")");
                fbody = fbody.replace(bodyMatcher.group()," " + argsList.get(paramCounter).toString() + (parenthesis ? ")" : " ")); //replace variable name in body with value from argslist 
                bodyMatcher.reset(fbody);
            }
            paramCounter++;
        }

        if (fbody.contains("if ") || fbody.contains("if("))
        {
            fbody = processDefunIf(fbody);
        }

        return fbody;
    }

    public static String processDefunIf(String command)
    {

        
        Pattern pattern = Pattern.compile("[[ ] [(]]{1}if[[ ][(]]{1}");
        Matcher matcher = pattern.matcher(command);
        int start = 0;
        int opencount = 1;
        int closedcount = 0;
        String processedBool = "";
        String group = "";

        matcher.find();
        start = matcher.end();

        matcher.usePattern(Pattern.compile("[(]{1}[[ ][<][!][>][=][or][and][not][0-9]]+[)]{1}"));
        matcher.find();
        group = matcher.group();
        processedBool = processCommand(group);

        command = command.replace(group, " " + processedBool + " ");
        matcher.reset(command);

        if(command.contains("NIL"))
        {
            matcher.usePattern(Pattern.compile("NIL"));
            matcher.find();
            matcher.usePattern(Pattern.compile("[[a-zA-Z0-9][.][-]['][_][(][)]][ ]{1}"));
            matcher.find(); //We have NIL, therefore skip over first expression
            matcher.usePattern(Pattern.compile("[[a-zA-Z0-9][.][-]['][ ][+][*][/][_][(][)]]+"));
            matcher.find();//will find the rest of the command after the then-block
            start = matcher.start();

            opencount = 0;
            closedcount = 0;
            int j  = start;
            do
            {
                if(command.substring(j, j + 1).equals("(")) opencount++;
                if(command.substring(j, j + 1).equals(")")) closedcount++;
                j++;
            }while (opencount != closedcount);

            command = command.substring(start, j);


        }
        else
        {   //todo: Fully implement regex to find then-block and execute

            command = "1";
        }


        return command;
    }

    public static String selectFunction(String functionName, LinkedList argsList)
    {
        String result = "";

        switch (functionName)
        {
            case "+":
                result = add(argsList);
                break;

            case "*":
                result = mult(argsList);
                break;

            case "-":
                result = minus(argsList);
                break;

            case "/":
                result = divide(argsList);
                break;

            case ">":
                result = great(argsList);
                break;

            case ">=":
                result = greateq(argsList);
                break;

            case "<":
                result = less(argsList);
                break;

            case "<=":
                result = lesseq(argsList);
                break;

            case "or":
                result = funor(argsList);
                break;

            case "not":
                if(argsList.size() != 1)
                {
                    errorAndAbort("too many arguments for NOT.");
                    return "";
                }
                result = funnot(argsList.get(0).toString());
                break;

            case "and":
                result = funand(argsList);
                break;
            
            case "=":
                result = funeq(argsList);
                break;

            case "!=":
                result = funnoteq(argsList);
                break;
            
            case "if":
                result = funif(argsList);
                break;
            
            case "car":
                result = funcarcdr(argsList, true);
                break;

            case "cdr":
                result = funcarcdr(argsList, false);
                break;

            case "cons":
                result = funcons(argsList);
                break;

            case "define":
            case "set!":
                result = define(argsList);
                break;

            case "sqrt":
                result = funsqrt(argsList);
                break;

            case "pow":
                result = funpow(argsList);
                break;

            case "defun":
                result = defun(argsList);
                break;

            default:

                if(defunList.containsKey(functionName))
                {   
                    result = processDefun(functionName, argsList);
                    result = processCommand(result);
                    break;
                }

                if(functionName.contains("Ä"))
                {
                    errorAndAbort("ERROR: A literal list was found in a place where a function name should be.");
                    return "";
                }
                else
                {
                    errorAndAbort("Function with name \""+ functionName + "\" not found.");
                    return "";
                }
                
        }
        //substitute with printtoprompt
        //System.out.println("INTERMEDIATE " + result);
        return " " + result + " ";

    }

    public static String defun(LinkedList argsList)
    {
        
        if(argsList.size() != 3)
        {
            errorAndAbort("ERROR: defun needs to be of the form: (defun functionName (paramlist) (behaviorImplementation))");
            return "ERROR in defun";
        }

        LinkedList<String> ll = new LinkedList<>();
        String functionname = argsList.get(0).toString();
        String paramlist = argsList.get(1).toString();
        String functionbody = argsList.get(2).toString();

        ll.add(paramlist);
        ll.add(functionbody);
        defunList.put(functionname, ll);

        
        //System.out.println("DEBUG DEFUN " + defunList.toString());

        return functionname;
    }

    public static String funcons(LinkedList argsList)
    {   

        String toBePut = argsList.get(0).toString();//element to be put in target
        String target = argsList.get(1).toString(); 
        target = litLists.get(target).toString(); //target list where the element will be put, split command for readability
        StringBuffer buff = new StringBuffer();

        //check if the argument is a list or a constant
        if(toBePut.contains("Ä"))
        {
            toBePut = litLists.get(toBePut).toString();
        }
        
        buff.append("'(" + toBePut + " " + target.substring(2,target.length()));
        return buff.toString();
       

    }

    public static String funsqrt(LinkedList argsList)
    {
        Double result = Double.parseDouble(argsList.get(0).toString());
        result = Math.sqrt(result);
        return result.toString();
    }

    public static String funpow(LinkedList argsList)
    {
        Double base = Double.parseDouble(argsList.get(0).toString());
        Double exponent = Double.parseDouble(argsList.get(1).toString());
        Double result = Math.pow(base, exponent);
        return result.toString();
    }


    public static String add(LinkedList argsList) /** function call for "+" */
    {   
        int i = 0;
        Double sum = 0.0;
        while(i < argsList.size())
        {
            if(isNumber(argsList.get(i).toString()))
            {
                sum += Double.parseDouble(argsList.get(i).toString());
            }
            else
            {   
                //System.out.println("at least one argument in + is not a number.");
                errorAndAbort("at least one argument in + is not a number.");
                return "";
            }
            i++;
        }
        if(sum % 1 == 0)
        {   
            Integer ret = sum.intValue();
            return  ret.toString();
        }
        return sum.toString();
    }

    public static String mult(LinkedList argsList) /** function call for "*" */
    {
        int i = 0;
        Double sum = 1.0;
        while(i < argsList.size())
        {
            if(isNumber(argsList.get(i).toString()))
            {
                sum *= Double.parseDouble(argsList.get(i).toString());
            }
            else
            {   
                //System.out.println("at least one argument in * is not a number.");
                errorAndAbort("at least one argument in * is not a number.");
                return "";
            }
            i++;
        }
        if(sum % 1 == 0)
        {   
            Integer ret = sum.intValue();
            return  ret.toString();
        }
        return sum.toString();  
    }



    public static String minus(LinkedList argsList) /** function call for "*" */
    {
        int i = 1;
        Double sum = 0.0;
        if(isNumber(argsList.get(0).toString()))
        {
            sum = Double.parseDouble(argsList.get(0).toString()) ;
        }
        else
        {
            //System.out.println("First argument of - couldnt be parsed to a number");
            errorAndAbort("First argument of - couldnt be parsed to a number");
            return "";
        }

        while(i < argsList.size())
        {
            if(isNumber(argsList.get(i).toString()))
            {
                sum -= Double.parseDouble(argsList.get(i).toString());
            }
            else
            {   
                //System.out.println("at least one argument in - is not a number.");
                errorAndAbort("at least one argument in - is not a number.");
                return "";
            }
            i++;
        }
        if(sum % 1 == 0)
        {   
            Integer ret = sum.intValue();
            return  ret.toString();
        }
        return sum.toString();
    }

    public static String divide(LinkedList argsList) /** function call for "*" */
    {
        int i = 1;
        Double sum = 0.0;
        if(isNumber(argsList.get(0).toString()))
        {
            sum = Double.parseDouble(argsList.get(0).toString());
        }
        else
        {
            //System.out.println("First argument of / couldnt be parsed to a number");
            errorAndAbort("First argument of / couldnt be parsed to a number");
            return "";
        }

        while(i < argsList.size())
        {
            if(isNumber(argsList.get(i).toString()))
            {   
                if(Double.parseDouble(argsList.get(i).toString()) != 0.0)
                {
                    sum /= Double.parseDouble(argsList.get(i).toString());
                }
                else
                {
                    //System.out.println("Divide by Zero Error.");
                    errorAndAbort("Divide by Zero Error.");
                    return "";
                }
            }
            else
            {   
                //System.out.println("at least one argument in divide is not a number.");
                errorAndAbort("at least one argument in divide is not a number.");
                return "";
            }
            i++;
        }
        if(sum % 1 == 0)
        {   
            Integer ret = sum.intValue();
            return  ret.toString();
        }
        return sum.toString();
    }


    public static String great(LinkedList argsList) /** function call for "*" */
    {

        int i = 0;

        while(i < argsList.size() - 1)
        {
            if(isNumber(argsList.get(i).toString()) && isNumber(argsList.get(i + 1).toString()))
            {   
                Double d1 = Double.parseDouble(argsList.get(i).toString());
                Double d2 = Double.parseDouble(argsList.get(i+1).toString());
                if(d2 >= d1) return "NIL";
            }
            else
            {   
                //.println("at least one argument in > is not a number.");
                errorAndAbort("at least one argument in > is not a number.");
                return "";
            }
            i++;
        }
       return "T";
    }

    public static String greateq(LinkedList argsList)
    {

        int i = 0;

        while(i < argsList.size() - 1)
        {
            if(isNumber(argsList.get(i).toString()) && isNumber(argsList.get(i + 1).toString()))
            {   
                Double d1 = Double.parseDouble(argsList.get(i).toString());
                Double d2 = Double.parseDouble(argsList.get(i+1).toString());
                if(d2 > d1) return "NIL";
            }
            else
            {   
                //System.out.println("at least one argument in > is not a number.");
                errorAndAbort("at least one argument in > is not a number.");
                return "";
            }
            i++;
        }
       return "T";
    }

    public static String less(LinkedList argsList)
    {

        int i = 0;

        while(i < argsList.size() - 1)
        {
            if(isNumber(argsList.get(i).toString()) && isNumber(argsList.get(i + 1).toString()))
            {   
                Double d1 = Double.parseDouble(argsList.get(i).toString());
                Double d2 = Double.parseDouble(argsList.get(i+1).toString());
                if(d2 <= d1) return "NIL";
            }
            else
            {   
                //System.out.println("at least one argument in > is not a number.");
                errorAndAbort("at least one argument in > is not a number.");
                return "";
            }
            i++;
        }
       return "T";
    }

    public static String lesseq(LinkedList argsList)
    {

        int i = 0;

        while(i < argsList.size() - 1)
        {
            if(isNumber(argsList.get(i).toString()) && isNumber(argsList.get(i + 1).toString()))
            {   
                Double d1 = Double.parseDouble(argsList.get(i).toString());
                Double d2 = Double.parseDouble(argsList.get(i+1).toString());
                if(d2 < d1) return "NIL";
            }
            else
            {   
                //System.out.println("at least one argument in > is not a number.");
                errorAndAbort("at least one argument in > is not a number.");
                return "";
            }
            i++;
        }
       return "T";
    }


    public static String funand(LinkedList argsList)
    {

        int i = 0;

        while(i < argsList.size())
        {
            if(isLispBool(argsList.get(i).toString()))
            {   
                if(argsList.get(i).toString().equals("NIL")) return "NIL";
            }
            else
            {   
                errorAndAbort("at least one argument in AND is not BOOL.");
                return "";
            }
            i++;
        }
       return "T";
    }

    public static String funnot(String lispBool)
    {
        if(!isLispBool(lispBool))
        {
            errorAndAbort("did not pass NIL or T to not function");
            return "";
        }

        if(lispBool.equals("NIL"))
         return "T";

        return "NIL";
    }

    public static String funeq(LinkedList argsList)
    {

        int i = 0;

        while(i < argsList.size() - 1)
        {
            if(isNumber(argsList.get(i).toString()) && isNumber(argsList.get(i + 1).toString()))
            {   
                Double d1 = Double.parseDouble(argsList.get(i).toString());
                Double d2 = Double.parseDouble(argsList.get(i+1).toString());
                if(!(d2.equals(d1))) return "NIL";
            }
            else
            {   
                //System.out.println("at least one argument in > is not a number.");
                errorAndAbort("at least one argument in > is not a number.");
                return "";
            }
            i++;
        }
       return "T";
    }


    public static String funnoteq(LinkedList argsList)
    {
        return funnot(funeq(argsList));
    }

    public static String funor(LinkedList argsList)
    {

        int i = 0;

        while(i < argsList.size())
        {
            if(isLispBool(argsList.get(i).toString()))
            {   
                if(argsList.get(i).toString().equals("T")) return "T";
            }
            else
            {   
                errorAndAbort("at least one argument in OR is not BOOL.");
                return "";
            }
            i++;
        }
       return "NIL";
    }

    public static String funif(LinkedList argsList)
    {
        if(argsList.size() != 3)
        {
            errorAndAbort("Error parsing if: it is not of the form (if (condition) (then-block) (else-block))");
            return "";
        }

        if(argsList.get(0).toString().equals("NIL"))
        {
            return (argsList.get(2).toString());
        }
        else
        {
            if(argsList.get(0).toString().equals("T"))
            {
                return argsList.get(1).toString();
            }
            else
            {
                errorAndAbort("Please only use expressions that resolve to T or NIL (uppercase only) for if condition.");
                return "";
            }
        }
        //return "";
    }

    public static String funcarcdr(LinkedList argsList, boolean car)
    {   
        if(argsList.size()!= 1)
        {
            errorAndAbort("ERROR: always call function car or cdr with one and only one argument");
            return "";
        }

        if(!argsList.get(0).toString().substring(0,1).equals("Ä"))
        {
            errorAndAbort("ERROR: argument for function car or cdr needs to be a list");
            return "";
        }

        int start = 0;
        int end = 0;
       
        boolean isList = false;

        String list = litLists.get(argsList.get(0).toString()).toString();        

        Pattern pattern = Pattern.compile("[']{0,1}[ ]*[(]{0,1}[ ]*[a-zA-Z0-9[Ä][.][-]]+");
        String innerList = list.substring(2, list.length()-1);
        //System.out.println("INNERLIST " + innerList);
        Matcher matcher = pattern.matcher(innerList);
        
        matcher.find();
        String groupy = matcher.group();

        
        //System.out.println(groupy);
        

        if(groupy.contains("("))
        {   
            if (groupy.contains("'"))/**return with preceding apostrophe, if the first element had one */
            {   
                start =  innerList.indexOf("'");
                
            }   
            else
            {
                isList = true;
                start = innerList.indexOf("(");
            }

            int opencount = 1;
            int closedcount = 0;

            int i = start;
            while(i < innerList.length())
            {
                i++;
                
                if(innerList.substring(i, i + 1).equals("(")) opencount++;
                if(innerList.substring(i, i + 1).equals(")")) closedcount++;
                if(opencount == closedcount) break;
            }

            end = i;
        }
        else
        {
            matcher.reset(innerList);
            matcher.usePattern(Pattern.compile(validVarName));
            matcher.find();
            start = matcher.start();
            end = matcher.end()-1;
        }

        if(car)
        {   
            
            return   (isList ? "'" : "") + innerList.substring(start, end+1);
        }
        else
        {
            //System.out.println("FUNCARCDR " + "'("+ innerList.substring(0,start) + innerList.substring(end+1, innerList.length()) + ")");
            return "'("+ innerList.substring(0,start) + innerList.substring(end+2, innerList.length()) + ")";
        }
       
    }
 


    public static String define(LinkedList argsList) /**Function call for define */
    {   
        if (argsList.size()!= 2)
        {
            //System.out.println("Error in define: incorrect number of arguments");
            errorAndAbort("Error in define: incorrect number of arguments");
            return "";
        }

        String varname = argsList.get(0).toString();
        if(isNumber(varname))
        {
            //System.out.println("ERROR: " + argsList.get(0)+ " is not a valid variable name.");
            errorAndAbort("ERROR: " + argsList.get(0) + " is not a valid variable name.");
            return "";
        }

        if(varname.equals("T")||varname.equals("NIL"))
        {   
            errorAndAbort("ERROR: Cannot define NIL or T as variable, they are boolean constants.");
            return "";
        }


       return setq(argsList.get(0).toString(), argsList.get(1).toString());
    }

    public static String setq(String name, String value)
    {
        /*if(value.contains("'("))
        {
            value = parseLists(value);
        }*/
        vars.put(name, value);
        return value;
    }



      /**General function used to discard the current command and tell the user what the error was. 
       * As this assignment doesn't require any debugging, all errors will lead to immediate
       * automatic abort.
      */
    public static void errorAndAbort(String message)
    {   
        errorOccurred = true;
        errorString = message;
    }

    /**Determines whether the given variable is NIL or T, reserved for boolean operations in Lisp */
    public static boolean isLispBool(String var)
    {
        return (var.equals("T") || var.equals("NIL")); 
    }

    /**Find out if the string is numberical */
    public static boolean isNumber(String symbol) { 
        try 
        {  
            Double.parseDouble(symbol);  
            return true;
        } 
        catch(NumberFormatException e)
        {  
            return false;  
        }  
      }
}

