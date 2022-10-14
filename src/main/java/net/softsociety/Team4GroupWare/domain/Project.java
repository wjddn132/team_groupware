package net.softsociety.Team4GroupWare.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    String pj_code;
    String employee_code;
    String company_name;
    String company_code;
    String pj_name;
    String pj_startdate;
    String pj_enddate;
    String pj_leader_name;
    String pj_content;
    int pj_state;
    int pj_percent;
    LocalDate enddate;
    LocalDate startdate;

    public void setDate() {
        this.enddate = LocalDate.parse(this.pj_enddate.split(" ")[0], DateTimeFormatter.ISO_DATE);
        this.startdate = LocalDate.parse(this.pj_startdate.split(" ")[0], DateTimeFormatter.ISO_DATE);
    }
}