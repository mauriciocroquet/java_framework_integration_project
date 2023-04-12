package client;

import java.util.ArrayList;
import java.util.List;

public class FTable {
    private List<List<Integer>> data = new ArrayList<List<Integer>>();
    private int columns;

    public FTable(int cols) {
        if (cols < 0) {
            throw new IllegalArgumentException("empty table must be >= 0");
        }
        this.columns = cols;
    }

    public int getRows() {
        return this.data.size();
    }

    public int getColumns() {
        return this.columns;
    }

    public void set(int row, int col, int value) {
        if (col < 0 || col >= this.columns) {
            throw new IllegalArgumentException(col + " Out of range (0 to " + this.columns + " columns available)");
        }
        if (row < 0) {
            throw new IllegalArgumentException("Row index < 0");
        }

        while (this.data.size() <= row) {
            List<Integer> newRow = new ArrayList<Integer>();
            for (int i = 0; i < this.columns; i++) {
                newRow.add(0);
            }
            this.data.add(newRow);
        }

        this.data.get(row).set(col, value);
    }

    public int get(int row, int col) {
        if (col < 0 || col >= this.columns) {
            throw new IllegalArgumentException(col + " Out of range (0 to " + this.columns + " columns available)");
        }

        if (row < 0 || row >= this.data.size()) {
            throw new IllegalArgumentException(col + " Out of range (0 to " + this.data.size() + " columns available)");
        }

        return this.data.get(row).get(col);
    }

    public void setRow(int row, Integer[] val) {
        if (val.length != this.columns) {
            throw new IllegalArgumentException("values array size: " + val.length + ", must match FTable column number (" + this.columns + ")");
        }

        for (int i = 0; i < val.length; i++) {
            this.set(row, i, val[i]);
        }
    }

    public void addRow(Integer[] val) {
        if (val.length != this.columns) {
            throw new IllegalArgumentException("values array size: " + val.length + ", must match FTable column number (" + this.columns + ")");
        }

        this.setRow(this.data.size(), val);
    }

    public Integer[] getRow(int row) {
        if (row < 0 || row >= this.data.size()) {
            throw new IllegalArgumentException("Row index (" + row + ") out of range (0.." + this.data.size() + ").");
        }

        Integer[] returnArray = new Integer[this.columns];
        for (int i = 0; i < this.columns; i++) {
            returnArray[i] = this.get(row, i);
        }
        return returnArray;
    }

    public int searchDestination(int neigbourAddress){
        for(int i = 0; i < this.data.size(); i++){
            if() // look for the first position of the row
        }
    }
}
