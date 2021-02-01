package edu.tamu.aser.accelerate;

public class AssertDetailInfo {

    // (or left right)
    private String left;
    private String right;

    // AND 或者 OR
    private String relation;

    public AssertDetailInfo() {
        left = right = "";
    }

    public static AssertDetailInfo getAssertDetailInfo(String ass) {
        String[] lines = ass.split("\\s+");
        AssertDetailInfo assertDetailInfo = new AssertDetailInfo();
        int parentthesis = 0;

        if (lines[0].contains("and"))
            assertDetailInfo.setRelation("AND");
        else if (lines[0].contains("or"))
            assertDetailInfo.setRelation("OR");
        else
            return null;//return ass;

        int i = 1;
        do {
            String line = lines[i];

            if (line.contains("("))
                parentthesis++;
            else if (line.contains(")"))
                parentthesis--;

            assertDetailInfo.addLeft(line + " ");

            ++i;
        } while (i < lines.length && parentthesis > 0);

        if (i < lines.length)
            do {
                String line = lines[i];

                if (line.contains("("))
                    parentthesis++;
                else if (line.contains(")"))
                    parentthesis--;

                assertDetailInfo.addRight(line + " ");

                ++i;
            } while (i < lines.length && parentthesis > 0);

        assertDetailInfo.removeSpaces();

        return assertDetailInfo;
    }

    public String getLeft() {
        return left;
    }

    public void addLeft(String left) {
        this.left += left;
    }

    public String getRight() {
        return right;
    }

    public void addRight(String right) {
        this.right += right;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public void removeSpaces() {
        if (right.endsWith(" ")) right = right.substring(0, right.length() - 1);
        if (left.endsWith(" ")) left = left.substring(0, left.length() - 1);
    }
}
