package org.movsim.consumption.offline;


// http://opencsv.sourceforge.net/#how-to-read
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.movsim.consumption.input.xml.batch.BatchDataInput;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Preconditions;

public class InputReader {

    private final char separator = ',';

    private final List<ConsumptionDataRecord> records;
    
    private final BatchDataInput batchInput;

    public static InputReader create(BatchDataInput batchInput, String path) {
        File inputFile = new File(path, batchInput.getInputFile());
        System.out.println("read input from " + inputFile.getAbsolutePath());
        
        return new InputReader(inputFile, batchInput);
    }

    private InputReader(File inputFile, BatchDataInput batchInput) {
        Preconditions.checkNotNull(inputFile);
        Preconditions.checkNotNull(batchInput);
        Preconditions.checkArgument(inputFile.exists() && inputFile.isFile(), "file=" + inputFile.getAbsolutePath()
                + " does not exist!");
        this.batchInput = batchInput;
        records = new LinkedList<ConsumptionDataRecord>();
        
        process(inputFile);
    }

    public List<ConsumptionDataRecord> getRecords() {
        return records;
    }

    private void process(File inputFile){
        List<String[]> inputDataLines = readData(inputFile);
        
        if (inputDataLines == null || inputDataLines.isEmpty()) {
            System.out.println("no input read");
            return;
        }

        parseInputData(inputDataLines);

    }

    private void parseInputData(List<String[]> input) {
       
        InputDataParser parser = new InputDataParser(batchInput.getColumnData(), batchInput.getConversionInput());
        
        int index = 0;
        for (String[] line : input) {
            try{
                ConsumptionDataRecord record = parser.parse(line);
                record.setIndex(index);
                records.add(record);
                ++index;
            } catch (NumberFormatException e) {
                System.out.println("cannot parse data. Ignore line=" + Arrays.toString(line));
            }
        }

        System.out.println("parsed " + records.size() + "(index=" + index + ")" + " from " + input.size()
                + " input lines");
    }

    private List<String[]> readData(File file){
        System.out.println("using input file " + file.getAbsolutePath());
        
        List<String[]> myEntries = null;
        CSVReader reader = null;
        try {
            reader = new CSVReader(new FileReader(file), separator);
            myEntries = reader.readAll();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally{
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return myEntries;
    }
    


}
